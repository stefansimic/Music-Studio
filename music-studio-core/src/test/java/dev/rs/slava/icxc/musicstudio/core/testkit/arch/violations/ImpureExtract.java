package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;

/** Deliberate violation of A12 (a pure feature using core.task), used by {@code ArchitectureRulesSelfTest}. */
public final class ImpureExtract {

    private ImpureExtract() {
    }

    static boolean cancelled(Cancellation cancel) {
        return cancel.isCancelled();
    }
}
