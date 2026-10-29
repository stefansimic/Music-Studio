package dev.rs.slava.icxc.musicstudio.core.tag;

/** Text encoding of the ID3v2 frames. */
public enum V2Encoding {
    /** Latin-1 if every character fits, otherwise UTF-16 (v2.2, v2.3) or UTF-8 (v2.4). */
    AUTO,
    /** ISO-8859-1 only; values that do not fit are rejected before writing. */
    LATIN1,
    /** UTF-16 with byte order mark. */
    UTF16,
    /** UTF-8; only valid with ID3v2.4. */
    UTF8
}
