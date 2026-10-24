package dev.rs.slava.icxc.musicstudio.core.model;

/**
 * How serious an {@link Issue} is. ERROR on an item: the item is not written (skipped or failed). ERROR on a plan:
 * the plan is not executable. WARNING: it is written, but the user should look at it. INFO: verbose output only.
 */
public enum Severity {
    /** Shown in verbose output. */
    INFO,
    /** Written, but worth a look. */
    WARNING,
    /** Not written / not executable. */
    ERROR
}
