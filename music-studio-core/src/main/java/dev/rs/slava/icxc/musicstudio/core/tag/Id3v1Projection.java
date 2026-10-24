package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.text.FittedText;
import dev.rs.slava.icxc.musicstudio.core.text.Id3v1Text;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Turns final metadata into ID3v1 content plus warnings (the {@code audio-metadata} rules): text is transliterated
 * (ASCII mode) and truncated at a word boundary; unmappable characters are dropped, never replaced by "?"; the genre
 * is the ID3v1 number of the primary genre, 12 "Other" for a genre without number, 255 for none.
 *
 * <p>Severities: transliterated, truncated, dropped characters, genre → Other and an unrepresentable track number
 * are WARNINGs; structural limits (album artist, disc, track total, extra genres, track in v1.0) are INFO.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Id3v1Projection {

    /** The genre number written for a genre that the ID3v1 table does not know. */
    public static final int OTHER = 12;

    private Id3v1Projection() {
    }

    /**
     * The projected values and the issues of the projection.
     *
     * @param values the ID3v1 content; not null
     * @param issues warnings/infos, each with its field; not null; copied
     */
    public record Result(Id3v1Values values, List<Issue> issues) {

        /**
         * Validates and copies the components.
         *
         * @throws NullPointerException if a component or element is null
         */
        public Result {
            Require.nonNull(values, "values");
            Require.noNullElements(issues, "issues");
            issues = List.copyOf(issues);
        }
    }

    /**
     * Projects metadata onto an ID3v1 tag.
     *
     * @param metadata the final metadata; not null
     * @param version  the ID3v1 version; not null, not NONE
     * @param mode     the character set; not null
     * @return the values and issues; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if {@code version} is NONE
     */
    public static Result project(MediaMetadata metadata, Id3v1Choice version, V1TextMode mode) {
        Require.nonNull(metadata, "metadata");
        Require.nonNull(version, "version");
        Require.that(version != Id3v1Choice.NONE, "version must not be NONE");
        Require.nonNull(mode, "mode");
        List<Issue> issues = new ArrayList<>();
        String label = version.version().orElseThrow().label();
        String title = text(metadata, Field.TITLE, Id3v1Values.TEXT_BYTES, mode, label, issues);
        String artist = text(metadata, Field.ARTIST, Id3v1Values.TEXT_BYTES, mode, label, issues);
        String album = text(metadata, Field.ALBUM, Id3v1Values.TEXT_BYTES, mode, label, issues);
        int commentBytes = version == Id3v1Choice.V1_1 ? Id3v1Values.V1_1_COMMENT_BYTES : Id3v1Values.TEXT_BYTES;
        String comment = text(metadata, Field.COMMENT, commentBytes, mode, label, issues);
        OptionalInt year = metadata.get(Field.YEAR)
                .map(v -> OptionalInt.of(((FieldValue.YearValue) v).year())).orElse(OptionalInt.empty());
        int track = track(metadata, version, label, issues);
        int genre = genre(metadata, label, issues);
        for (Field field : new Field[] {Field.ALBUM_ARTIST, Field.DISC}) {
            if (metadata.get(field).isPresent()) {
                issues.add(Issue.info(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                        label + " has no " + field.placeholder() + " field").forField(field));
            }
        }
        return new Result(new Id3v1Values(version, mode, title, artist, album, year, comment, track, genre), issues);
    }

    private static String text(MediaMetadata metadata, Field field, int maxBytes, V1TextMode mode, String label,
                               List<Issue> issues) {
        String value = metadata.display(field).orElse("");
        if (value.isEmpty()) {
            return "";
        }
        FittedText fitted = Id3v1Text.fit(value, mode.asciiOnly(), maxBytes);
        String where = label + " " + field.placeholder() + ": ";
        if (fitted.transliterated()) {
            issues.add(Issue.warning(IssueCode.V1_TRANSLITERATED,
                    where + "\"" + value + "\" written as \"" + fitted.text() + "\"").forField(field));
        }
        if (fitted.truncated()) {
            issues.add(Issue.warning(IssueCode.V1_TRUNCATED,
                    where + "cut to " + maxBytes + " characters: \"" + fitted.text() + "\"").forField(field));
        }
        if (fitted.dropped()) {
            issues.add(Issue.warning(IssueCode.V1_CHAR_DROPPED,
                    where + "characters that " + mode + " cannot hold were left out").forField(field));
        }
        return fitted.text();
    }

    private static int track(MediaMetadata metadata, Id3v1Choice version, String label, List<Issue> issues) {
        if (metadata.get(Field.TRACK).isEmpty()) {
            return 0;
        }
        FieldValue.Position position = (FieldValue.Position) metadata.get(Field.TRACK).get();
        if (version == Id3v1Choice.V1_0) {
            issues.add(Issue.info(IssueCode.V1_FIELD_NOT_REPRESENTABLE, label + " has no track number")
                    .forField(Field.TRACK));
            return 0;
        }
        if (position.number() > 255) {
            issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                    label + " track: " + position.number() + " is greater than 255 and is left out")
                    .forField(Field.TRACK));
            return 0;
        }
        if (position.total().isPresent()) {
            issues.add(Issue.info(IssueCode.V1_FIELD_NOT_REPRESENTABLE, label + " has no track total")
                    .forField(Field.TRACK));
        }
        return position.number();
    }

    private static int genre(MediaMetadata metadata, String label, List<Issue> issues) {
        if (metadata.get(Field.GENRE).isEmpty()) {
            return Id3v1Genres.NONE;
        }
        FieldValue.Genres genres = (FieldValue.Genres) metadata.get(Field.GENRE).get();
        if (genres.values().size() > 1) {
            issues.add(Issue.info(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                    label + " holds one genre; only \"" + genres.primary().name() + "\" is written")
                    .forField(Field.GENRE));
        }
        OptionalInt number = Id3v1Genres.number(genres.primary().name());
        if (number.isPresent()) {
            return number.getAsInt();
        }
        issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE, label + " genre: \"" + genres.primary().name()
                + "\" is not in the ID3v1 genre list; written as \"Other\"").forField(Field.GENRE));
        return OTHER;
    }
}
