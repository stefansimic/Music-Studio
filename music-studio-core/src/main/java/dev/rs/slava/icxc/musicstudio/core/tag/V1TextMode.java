package dev.rs.slava.icxc.musicstudio.core.tag;

/** How Latin-script text is stored in the ID3v1 tag. */
public enum V1TextMode {
    /** Accents are folded to ASCII (ü becomes ue). */
    ASCII,
    /** ISO-8859-1 characters are kept (ü stays ü); other letters are folded to ASCII. */
    LATIN1
}
