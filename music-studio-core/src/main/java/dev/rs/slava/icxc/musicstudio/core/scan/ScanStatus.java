package dev.rs.slava.icxc.musicstudio.core.scan;

/** How a scan ended. */
public enum ScanStatus {
    /** Every file was delivered to the sink. */
    COMPLETED,
    /** The user cancelled; the sink received a prefix of the sorted files. */
    CANCELLED,
    /** The root could not be listed; the sink received nothing. */
    FAILED
}
