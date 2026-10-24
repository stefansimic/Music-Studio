package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Byte-exact ID3v1 content, derived by core ({@link Id3v1Projection}) so every adapter writes exactly what the dry
 * run showed.
 *
 * @param version not NONE
 * @param mode    character set of the strings; not null
 * @param title   ≤ 30 chars, printable characters of {@code mode}; not null ("" = empty)
 * @param artist  as title
 * @param album   as title
 * @param year    1..9999 if present; not null
 * @param comment ≤ 30 (V1_0) / 28 (V1_1) chars of {@code mode}; not null
 * @param track   0..255 (0 = none); 0 for V1_0
 * @param genre   0..255 (255 = none)
 */
public record Id3v1Values(Id3v1Choice version, V1TextMode mode, String title, String artist, String album,
                          OptionalInt year, String comment, int track, int genre) {

    /** Bytes of title, artist, album and the v1.0 comment. */
    public static final int TEXT_BYTES = 30;
    /** Bytes of the v1.1 comment. */
    public static final int V1_1_COMMENT_BYTES = 28;

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if an invariant is violated
     */
    public Id3v1Values {
        Require.nonNull(version, "version");
        Require.that(version != Id3v1Choice.NONE, "version must not be NONE");
        Require.nonNull(mode, "mode");
        text(title, TEXT_BYTES, mode, "title");
        text(artist, TEXT_BYTES, mode, "artist");
        text(album, TEXT_BYTES, mode, "album");
        Require.nonNull(year, "year");
        year.ifPresent(y -> Require.inRange(y, 1, 9999, "year"));
        text(comment, version == Id3v1Choice.V1_1 ? V1_1_COMMENT_BYTES : TEXT_BYTES, mode, "comment");
        Require.inRange(track, 0, 255, "track");
        Require.that(version == Id3v1Choice.V1_1 || track == 0, "ID3v1.0 has no track number");
        Require.inRange(genre, 0, 255, "genre");
    }

    private static void text(String value, int maxBytes, V1TextMode mode, String name) {
        Require.nonNull(value, name);
        Require.that(value.length() <= maxBytes, "%s is longer than %d bytes", name, maxBytes);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = c >= 0x20 && c <= 0x7E || mode == V1TextMode.LATIN1 && c >= 0xA0 && c <= 0xFF;
            Require.that(ok, "%s contains a character that %s cannot hold: U+%04X", name, mode, (int) c);
        }
    }

    /**
     * What a TagReader reports for this block: empty strings absent, track 0 absent, genre 255 (or unknown)
     * absent, year as is.
     *
     * @return the metadata; never null
     */
    public MediaMetadata readBack() {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        put(values, Field.TITLE, title);
        put(values, Field.ARTIST, artist);
        put(values, Field.ALBUM, album);
        put(values, Field.COMMENT, comment);
        year.ifPresent(y -> values.put(Field.YEAR, new FieldValue.YearValue(y)));
        if (track > 0) {
            values.put(Field.TRACK, FieldValue.Position.of(track));
        }
        Id3v1Genres.name(genre).ifPresent(name -> values.put(Field.GENRE, FieldValue.Genres.of(name)));
        return new MediaMetadata(values);
    }

    private static void put(Map<Field, FieldValue> values, Field field, String text) {
        String stripped = text.strip();
        if (!stripped.isEmpty()) {
            values.put(field, new FieldValue.Text(stripped));
        }
    }
}
