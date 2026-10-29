package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Compares what was read back from a written file with what should have been written. Pure.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class WriteVerifier {

    private WriteVerifier() {}

    /**
     * @param actual     the re-read result of the written file; not null
     * @param options    the options effective for this file; not null
     * @param expectedV2 what the ID3v2 slot must hold for the {@code writable} fields; ignored if no ID3v2 is
     *                   written; not null
     * @param writable   the fields the writer stores; not null
     * @param id3v1      the exact ID3v1 content, empty if none is written; not null
     * @return {@code VERIFY_MISMATCH} errors, one per difference; empty if the file is as expected; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<Issue> verify(TagReadResult actual, TagWriteOptions options, MediaMetadata expectedV2,
                                     Set<Field> writable, Optional<Id3v1Values> id3v1) {
        Require.nonNull(actual, "actual");
        Require.nonNull(options, "options");
        Require.nonNull(expectedV2, "expectedV2");
        Require.noNullElements(writable, "writable");
        Require.nonNull(id3v1, "id3v1");
        List<Issue> issues = new ArrayList<>();
        Optional<TagLayer> v2 = layer(actual, TagFamily.ID3V2);
        Optional<TagLayer> v1 = layer(actual, TagFamily.ID3V1);

        if (options.id3v2() != Id3v2Choice.NONE) {
            TagVersion wanted = options.id3v2().version().orElseThrow();
            if (v2.isEmpty() || v2.get().version() != wanted) {
                issues.add(mismatch("expected " + wanted.label() + " but found "
                        + v2.map(l -> l.version().label()).orElse("no ID3v2 tag")));
            } else {
                for (Field field : writable) {
                    Optional<FieldValue> want = expectedV2.get(field).filter(v -> !isZeroPosition(v));
                    Optional<FieldValue> got = v2.get().metadata().get(field).filter(v -> !isZeroPosition(v));
                    if (!want.equals(got)) {
                        issues.add(mismatch(field.placeholder() + " is '" + got.map(FieldValue::display).orElse("")
                                + "' but should be '" + want.map(FieldValue::display).orElse("") + "'").forField(field));
                    }
                }
            }
        } else if (options.removeOtherVersions() && v2.isPresent()) {
            issues.add(mismatch("an ID3v2 tag is still present"));
        }

        if (id3v1.isPresent()) {
            Id3v1Values values = id3v1.get();
            TagVersion wanted = values.version().version().orElseThrow();
            // an ID3v1.1 tag without a track number has the same bytes as ID3v1.0 and reads back as ID3v1.0
            boolean sameBytes = wanted == TagVersion.ID3V1_1 && values.track() == 0
                    && v1.isPresent() && v1.get().version() == TagVersion.ID3V1_0;
            if (v1.isEmpty() || (v1.get().version() != wanted && !sameBytes)) {
                issues.add(mismatch("expected " + wanted.label() + " but found "
                        + v1.map(l -> l.version().label()).orElse("no ID3v1 tag")));
            } else {
                MediaMetadata read = v1.get().metadata();
                compareText(issues, read, Field.TITLE, values.title(), values);
                compareText(issues, read, Field.ARTIST, values.artist(), values);
                compareText(issues, read, Field.ALBUM, values.album(), values);
                compareText(issues, read, Field.COMMENT, values.comment(), values);
                Optional<FieldValue> year = read.get(Field.YEAR);
                if (!(year.isPresent() ? year.get() instanceof FieldValue.YearValue y && values.year().isPresent()
                        && y.year() == values.year().getAsInt() : values.year().isEmpty())) {
                    issues.add(mismatch("year differs").forField(Field.YEAR));
                }
                if (values.version() == Id3v1Choice.V1_1) {
                    int track = read.get(Field.TRACK).orElse(null) instanceof FieldValue.Position p ? p.number() : 0;
                    if (track != values.track()) {
                        issues.add(mismatch("track is " + track + " but should be " + values.track())
                                .forField(Field.TRACK));
                    }
                }
                Optional<String> genre = read.display(Field.GENRE);
                Optional<String> wantGenre = values.genre() == Id3v1Values.NO_GENRE
                        ? Optional.empty() : Id3v1Genres.name(values.genre());
                if (!genre.equals(wantGenre)) {
                    issues.add(mismatch("genre is '" + genre.orElse("") + "' but should be '"
                            + wantGenre.orElse("") + "'").forField(Field.GENRE));
                }
            }
        } else if (options.id3v1() == Id3v1Choice.NONE && options.removeOtherVersions() && v1.isPresent()) {
            issues.add(mismatch("an ID3v1 tag is still present"));
        }
        return List.copyOf(issues);
    }

    /** A track or disc number 0 is stored but read back as "not set", so it is the same as no value. */
    private static boolean isZeroPosition(FieldValue value) {
        return value instanceof FieldValue.Position position && position.number() == 0;
    }

    /** ID3v1 bytes are compared, not strings: the reader decodes them as ISO-8859-1 whatever codepage was written. */
    private static void compareText(List<Issue> issues, MediaMetadata read, Field field, String expected,
                                    Id3v1Values values) {
        String wantBytes = new String(expected.getBytes(values.encoding().charset()), StandardCharsets.ISO_8859_1);
        String got = read.display(field).orElse("");
        if (!wantBytes.equals(got)) {
            issues.add(mismatch(field.placeholder() + " in the ID3v1 tag is '" + got + "' but should be '"
                    + wantBytes + "'").forField(field));
        }
    }

    private static Optional<TagLayer> layer(TagReadResult result, TagFamily family) {
        return result.layers().stream().filter(l -> l.version().family() == family).findFirst();
    }

    private static Issue mismatch(String message) {
        return Issue.error(IssueCode.VERIFY_MISMATCH, "Written file differs from the plan: " + message);
    }
}
