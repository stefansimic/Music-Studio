package dev.rs.slava.icxc.musicstudio.core.scan;

/** How a scan ended. */
public enum ScanStatus {
    /** Every file was delivered. */
    COMPLETED,
    /** Cancelled; the sink received a prefix of the order. */
    CANCELLED,
    /** The root could not be listed; the sink received nothing. */
    FAILED
}
