package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.text.Id3v1Text;
import dev.rs.slava.icxc.musicstudio.core.text.Transliterator;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Turns the metadata an ID3v1 tag should hold into byte-exact {@link Id3v1Values} plus the warnings the dry-run shows.
 * Pure; the single place where ID3v1 text is converted, so every adapter writes exactly what was previewed.
 *
 * <p>Rules: album artist, disc (and track for v1.0) have no v1 slot. Non-Latin letters (Greek, Cyrillic, CJK, …) in a
 * text field are handled by {@link V1NonLatinPolicy}; everything else follows {@link V1TextMode}. Characters without a
 * mapping are dropped and reported, never replaced with {@code ?}. Truncation prefers a word boundary.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Id3v1Projection {

    private static final int OTHER_GENRE = 12;

    private Id3v1Projection() {}

    /**
     * Result of {@link #project}.
     *
     * @param values  the ID3v1 content; empty if ID3v1 is not written (option NONE, or skipped for non-Latin text)
     * @param issues  warnings and notes; not null
     * @param skipped true if an ID3v1 tag was requested but is not written for this file
     */
    public record Projection(Optional<Id3v1Values> values, List<Issue> issues, boolean skipped) {
        public Projection {
            Require.nonNull(values, "values");
            Require.noNullElements(issues, "issues");
            issues = List.copyOf(issues);
        }
    }

    /**
     * @param metadata the content the ID3v1 tag should hold (the final metadata of the file); not null
     * @param options  the write options; not null
     * @return the projection; empty values if {@code options.id3v1()} is NONE (no issues) or if v1 is skipped
     * @throws NullPointerException if an argument is null
     */
    public static Projection project(MediaMetadata metadata, TagWriteOptions options) {
        Require.nonNull(metadata, "metadata");
        Require.nonNull(options, "options");
        Id3v1Choice version = options.id3v1();
        if (version == Id3v1Choice.NONE) {
            return new Projection(Optional.empty(), List.of(), false);
        }
        List<Issue> issues = new ArrayList<>();
        notRepresentable(metadata, version, issues);

        Map<Field, String> texts = new EnumMap<>(Field.class);
        for (Field field : new Field[] {Field.TITLE, Field.ARTIST, Field.ALBUM, Field.COMMENT}) {
            texts.put(field, metadata.display(field).map(v -> v.replaceAll("\\R", " ").strip()).orElse(""));
        }
        List<Field> nonLatin = new ArrayList<>();
        boolean greek = false;
        boolean cyrillic = false;
        for (Map.Entry<Field, String> entry : texts.entrySet()) {
            boolean fieldNonLatin = false;
            for (int i = 0; i < entry.getValue().length(); ) {
                int cp = entry.getValue().codePointAt(i);
                i += Character.charCount(cp);
                if (!isNonLatinLetter(cp)) {
                    continue;
                }
                fieldNonLatin = true;
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                greek |= script == Character.UnicodeScript.GREEK;
                cyrillic |= script == Character.UnicodeScript.CYRILLIC;
            }
            if (fieldNonLatin) {
                nonLatin.add(entry.getKey());
            }
        }

        V1Encoding encoding = options.v1TextMode() == V1TextMode.ASCII ? V1Encoding.ASCII : V1Encoding.LATIN1;
        if (!nonLatin.isEmpty()) {
            switch (options.v1NonLatinPolicy()) {
                case SKIP_V1 -> {
                    return skipped(issues, "ID3v1 is not written: " + names(nonLatin) + " contain non-Latin letters. "
                            + "The ID3v2 tag keeps the original text");
                }
                case CODEPAGE -> {
                    if (greek && cyrillic) {
                        return skipped(issues, "ID3v1 is not written: " + names(nonLatin) + " mix Greek and Cyrillic, "
                                + "which need different codepages. The ID3v2 tag keeps the original text");
                    }
                    if (greek || cyrillic) {
                        encoding = greek ? V1Encoding.WINDOWS_1253 : V1Encoding.WINDOWS_1251;
                        issues.add(Issue.info(IssueCode.V1_LEGACY_CODEPAGE, "ID3v1 text is written in "
                                + (greek ? "Windows-1253 (Greek)" : "Windows-1251 (Cyrillic)")
                                + ", which only devices set to that locale display correctly"));
                    }
                }
                case TRANSLITERATE -> { }
            }
        }

        int commentLimit = version == Id3v1Choice.V1_1 ? 28 : 30;
        String title = fit(texts.get(Field.TITLE), encoding, 30, Field.TITLE, issues);
        String artist = fit(texts.get(Field.ARTIST), encoding, 30, Field.ARTIST, issues);
        String album = fit(texts.get(Field.ALBUM), encoding, 30, Field.ALBUM, issues);
        String comment = fit(texts.get(Field.COMMENT), encoding, commentLimit, Field.COMMENT, issues);
        OptionalInt year = metadata.get(Field.YEAR).orElse(null) instanceof FieldValue.YearValue value
                ? OptionalInt.of(value.year()) : OptionalInt.empty();
        int track = 0;
        if (version == Id3v1Choice.V1_1 && metadata.get(Field.TRACK).orElse(null) instanceof FieldValue.Position p) {
            if (p.number() > 255) {
                issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                        "ID3v1.1 track number is at most 255, not " + p.number()).forField(Field.TRACK));
            } else {
                track = p.number();
            }
        }
        int genre = genre(metadata, issues);
        return new Projection(Optional.of(new Id3v1Values(version, encoding, title, artist, album, year, comment,
                track, genre)), issues, false);
    }

    private static boolean isNonLatinLetter(int cp) {
        if (!Character.isLetter(cp)) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON
                && script != Character.UnicodeScript.INHERITED;
    }

    private static Projection skipped(List<Issue> issues, String message) {
        issues.add(Issue.warning(IssueCode.V1_SKIPPED_NON_LATIN, message));
        return new Projection(Optional.empty(), issues, true);
    }

    private static String names(List<Field> fields) {
        return String.join(", ", fields.stream().map(f -> f.name().toLowerCase().replace('_', ' ')).toList());
    }

    private static void notRepresentable(MediaMetadata metadata, Id3v1Choice version, List<Issue> issues) {
        for (Field field : new Field[] {Field.ALBUM_ARTIST, Field.DISC}) {
            if (metadata.get(field).isPresent()) {
                issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                        "ID3v1 has no " + field.placeholder() + " field").forField(field));
            }
        }
        if (version == Id3v1Choice.V1_0 && metadata.get(Field.TRACK).isPresent()) {
            issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                    "ID3v1.0 has no track number; use ID3v1.1").forField(Field.TRACK));
        }
    }

    private static int genre(MediaMetadata metadata, List<Issue> issues) {
        if (!(metadata.get(Field.GENRE).orElse(null) instanceof FieldValue.Genres genres)) {
            return Id3v1Values.NO_GENRE;
        }
        if (genres.values().size() > 1) {
            issues.add(Issue.info(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                    "ID3v1 holds one genre; only " + genres.primary().name() + " is written").forField(Field.GENRE));
        }
        OptionalInt number = Id3v1Genres.number(genres.primary().name());
        if (number.isPresent()) {
            return number.getAsInt();
        }
        issues.add(Issue.warning(IssueCode.V1_FIELD_NOT_REPRESENTABLE,
                "'" + genres.primary().name() + "' is not an ID3v1 genre; written as Other").forField(Field.GENRE));
        return OTHER_GENRE;
    }

    private static String fit(String value, V1Encoding encoding, int maxBytes, Field field, List<Issue> issues) {
        if (value.isEmpty()) {
            return "";
        }
        boolean transliterated;
        boolean truncated;
        boolean dropped;
        String text;
        if (encoding == V1Encoding.ASCII || encoding == V1Encoding.LATIN1) {
            Id3v1Text.FittedText fitted = Id3v1Text.fit(value, encoding == V1Encoding.ASCII, maxBytes);
            text = fitted.text();
            transliterated = fitted.transliterated();
            truncated = fitted.truncated();
            dropped = fitted.dropped();
        } else {
            StringBuilder out = new StringBuilder();
            boolean changed = false;
            boolean lost = false;
            CharsetEncoder encoder = encoding.charset().newEncoder();
            for (int i = 0; i < value.length(); ) {
                int cp = value.codePointAt(i);
                i += Character.charCount(cp);
                String one = new String(Character.toChars(cp));
                if (encoder.canEncode(one)) {
                    out.append(one);
                    continue;
                }
                String folded = Transliterator.toAscii(one).text();
                if (folded.isBlank()) {
                    lost = true;
                } else {
                    out.append(folded);
                    changed = true;
                }
            }
            String stripped = out.toString().strip();
            truncated = stripped.length() > maxBytes;
            text = Id3v1Text.truncate(stripped, maxBytes);
            transliterated = changed;
            dropped = lost;
        }
        if (transliterated) {
            issues.add(Issue.warning(IssueCode.V1_TRANSLITERATED,
                    "ID3v1 " + field.placeholder() + " was converted to fit: '" + value + "' -> '" + text + "'")
                    .forField(field));
        }
        if (dropped) {
            issues.add(Issue.warning(IssueCode.V1_CHAR_DROPPED,
                    "Characters without an ID3v1 equivalent were left out of " + field.placeholder()).forField(field));
        }
        if (truncated) {
            issues.add(Issue.warning(IssueCode.V1_TRUNCATED,
                    "ID3v1 " + field.placeholder() + " is limited to " + maxBytes + " characters").forField(field));
        }
        return text;
    }
}
