package dev.rs.slava.icxc.musicstudio.core.export;

/** How an apply run ended. */
public enum WriteStatus {
    /** Every planned item was handled (written, failed or skipped). */
    COMPLETED,
    /** Cancelled; the outcomes list the items handled before. */
    CANCELLED,
    /** The plan was not executable (plan-wide error); nothing was written. */
    REJECTED
}
