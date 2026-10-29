package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

/** Deliberate violation of A2/A11 for the rules' self-test. */
public final class PrintsAndReflects {
    public Class<?> load(String name) throws ClassNotFoundException {
        System.out.println(name);
        return Class.forName(name);
    }
}
