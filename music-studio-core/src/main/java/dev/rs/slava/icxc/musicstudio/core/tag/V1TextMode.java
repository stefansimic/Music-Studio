package dev.rs.slava.icxc.musicstudio.core.tag;

/** Character set of ID3v1 text. */
public enum V1TextMode {
    /** Printable ASCII; other characters are transliterated (default; safest for old head units). */
    ASCII,
    /** Printable ISO-8859-1. */
    LATIN1;

    /** @return true for ASCII */
    public boolean asciiOnly() {
        return this == ASCII;
    }
}
