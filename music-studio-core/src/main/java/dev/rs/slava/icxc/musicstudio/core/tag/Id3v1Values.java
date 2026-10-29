package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.charset.CharsetEncoder;
import java.util.OptionalInt;

/**
 * Byte-exact ID3v1 content derived by core (transliteration, codepage, truncation, genre number), so every adapter
 * writes exactly what the dry-run showed: {@code text.getBytes(encoding.charset())}.
 *
 * <p>An ID3v1.1 tag whose track is 0 has the same bytes as an ID3v1.0 tag and reads back as ID3v1.0.
 *
 * <p>Strings fit their field in the charset of {@code encoding}: title, artist and album 30 bytes, comment 30
 * bytes for v1.0 and 28 for v1.1.
 *
 * @param version  V1_0 or V1_1; not null, not NONE
 * @param encoding byte encoding of the text; not null
 * @param title    at most 30 bytes, encodable in {@code encoding}; not null (empty = none)
 * @param artist   as title
 * @param album    as title
 * @param year     1..9999 if present; not null
 * @param comment  at most 30 (v1.0) or 28 (v1.1) bytes; not null
 * @param track    0..255, 0 = none, must be 0 for v1.0
 * @param genre    0..255, 255 = none
 */
public record Id3v1Values(Id3v1Choice version, V1Encoding encoding, String title, String artist, String album,
                          OptionalInt year, String comment, int track, int genre) {

    /** Genre byte meaning "no genre". */
    public static final int NO_GENRE = 255;

    public Id3v1Values {
        Require.nonNull(version, "version");
        Require.that(version != Id3v1Choice.NONE, "version must be V1_0 or V1_1");
        Require.nonNull(encoding, "encoding");
        Require.nonNull(title, "title");
        Require.nonNull(artist, "artist");
        Require.nonNull(album, "album");
        Require.nonNull(year, "year");
        Require.nonNull(comment, "comment");
        Require.that(year.isEmpty() || (year.getAsInt() >= 1 && year.getAsInt() <= 9999), "year must be 1..9999");
        fits(encoding, title, 30, "title");
        fits(encoding, artist, 30, "artist");
        fits(encoding, album, 30, "album");
        fits(encoding, comment, version == Id3v1Choice.V1_1 ? 28 : 30, "comment");
        Require.inRange(track, 0, 255, "track");
        Require.that(version == Id3v1Choice.V1_1 || track == 0, "track must be 0 for V1_0");
        Require.inRange(genre, 0, 255, "genre");
    }

    private static void fits(V1Encoding encoding, String text, int maxBytes, String name) {
        CharsetEncoder encoder = encoding.charset().newEncoder();
        Require.that(encoder.canEncode(text), "%s cannot be encoded as %s", name, encoding);
        Require.that(text.length() <= maxBytes && text.getBytes(encoding.charset()).length <= maxBytes,
                "%s is longer than %d bytes", name, maxBytes);
    }
}
