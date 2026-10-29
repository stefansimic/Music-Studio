package dev.rs.slava.icxc.musicstudio.core.tag;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** The byte encoding of the text in an ID3v1 tag, as written. */
public enum V1Encoding {
    /** US-ASCII. */
    ASCII("US-ASCII"),
    /** ISO-8859-1. */
    LATIN1("ISO-8859-1"),
    /** Windows-1253 (Greek). */
    WINDOWS_1253("windows-1253"),
    /** Windows-1251 (Cyrillic). */
    WINDOWS_1251("windows-1251");

    private final String charsetName;

    V1Encoding(String charsetName) {
        this.charsetName = charsetName;
    }

    /**
     * @return the charset of this encoding; never null
     * @throws java.nio.charset.UnsupportedCharsetException if the runtime does not include the charset
     */
    public Charset charset() {
        return switch (this) {
            case ASCII -> StandardCharsets.US_ASCII;
            case LATIN1 -> StandardCharsets.ISO_8859_1;
            default -> Charset.forName(charsetName);
        };
    }
}
