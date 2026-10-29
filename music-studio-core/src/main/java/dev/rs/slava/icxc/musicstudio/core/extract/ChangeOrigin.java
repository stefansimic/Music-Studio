package dev.rs.slava.icxc.musicstudio.core.extract;

/** Which step of the pipeline set a proposed value. */
public enum ChangeOrigin {
    /** Extracted from the file or folder name. */
    PATTERN,
    /** A fixed value for all files. */
    FIXED,
    /** Track number from auto-numbering. */
    AUTO_NUMBER,
    /** Edited by hand. */
    MANUAL
}
