package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

/** Deliberate violation of A6 (non-final field in core), used by {@code ArchitectureRulesSelfTest}. */
public final class MutableField {

    private int counter;

    int next() {
        return ++counter;
    }
}
