package dev.rs.slava.icxc.musicstudio.core.export;

/** How an export run ended. */
public enum ExportStatus {
    /** Every planned write succeeded. */
    COMPLETED,
    /** The run finished but at least one file failed. */
    COMPLETED_WITH_ERRORS,
    /** The user cancelled; files written so far stay. */
    CANCELLED,
    /** A fatal condition (full disk, output folder not creatable) stopped the run. */
    ABORTED
}
