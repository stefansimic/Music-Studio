package dev.rs.slava.icxc.musicstudio.core.model;

/**
 * A tag slot in a file. A file holds at most one tag per family. The declaration order is the precedence for
 * effective values (first wins).
 */
public enum TagFamily {
    /** ID3v2.x at the start of an MP3 file. */
    ID3V2,
    /** iTunes-style atoms of an MP4 file. */
    MP4,
    /** ID3v1.x in the last 128 bytes of an MP3 file. */
    ID3V1
}
