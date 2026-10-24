package dev.rs.slava.icxc.musicstudio.core.tag;

/** Text encoding of the written ID3v2 text frames of the modelled fields. */
public enum V2Encoding {
    /** Latin-1 where the value fits, otherwise UTF-16 (v2.2/v2.3) or UTF-8 (v2.4). */
    AUTO,
    /** ISO-8859-1 only; values with other characters cannot be written. */
    LATIN1,
    /** UTF-16 with byte order mark. */
    UTF16,
    /** UTF-8 (ID3v2.4 only). */
    UTF8
}
