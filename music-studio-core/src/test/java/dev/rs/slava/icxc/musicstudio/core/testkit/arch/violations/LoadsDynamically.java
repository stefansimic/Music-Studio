package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import java.util.ServiceLoader;
import java.util.prefs.Preferences;

/** Deliberate violations of A1/A2/A11 (preferences, processes, dynamic loading), used by the self-test. */
public final class LoadsDynamically {

    private LoadsDynamically() {
    }

    static Object load(ClassLoader loader) throws Exception {
        return loader.loadClass("java.lang.String");
    }

    static Object services() {
        return ServiceLoader.load(Runnable.class);
    }

    static Object prefs() {
        return Preferences.userRoot();
    }

    static Object process() {
        return new ProcessBuilder("true");
    }

    static String env() {
        return System.getenv("HOME");
    }
}
