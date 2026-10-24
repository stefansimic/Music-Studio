package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

/** Deliberate violation of A11 (Class.forName), used by {@code ArchitectureRulesSelfTest}. */
public final class UsesReflection {

    private UsesReflection() {
    }

    static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
}
