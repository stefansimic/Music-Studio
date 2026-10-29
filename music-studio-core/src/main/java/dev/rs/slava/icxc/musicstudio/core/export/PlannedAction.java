package dev.rs.slava.icxc.musicstudio.core.export;

/** What apply does with a planned item. */
public enum PlannedAction {
    /** The file is written. */
    WRITE,
    /** The file is not written; the issues say why (error, or nothing to do). */
    SKIP
}
