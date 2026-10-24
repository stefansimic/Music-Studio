package dev.rs.slava.icxc.musicstudio.core.extract;

/** Where a proposed field value comes from. */
public enum ChangeOrigin {
    /** Extracted from the file/folder name. */
    PATTERN,
    /** A fixed value for all files. */
    FIXED,
    /** Track auto-numbering. */
    AUTO_NUMBER,
    /** A manual edit. */
    MANUAL
}
