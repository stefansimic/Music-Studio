package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

/** Deliberate violation of A6 for the rules' self-test. */
public final class MutableState {
    private int counter;

    public int next() {
        return ++counter;
    }
}
