package dev.rs.slava.icxc.musicstudio.core.export;

/** What the apply step does with a planned file. */
public enum PlannedAction {
    /** Write a tagged copy to the target. */
    WRITE,
    /** Do nothing for this file; the issues say why. */
    SKIP
}
