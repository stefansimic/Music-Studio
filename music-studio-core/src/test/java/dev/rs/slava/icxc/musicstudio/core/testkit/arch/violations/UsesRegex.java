package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import java.util.regex.Pattern;

/** Deliberate violation of D02 (regex) for the rules' self-test. Never used by production code. */
public final class UsesRegex {
    public boolean matches(String text) {
        return Pattern.compile("a+").matcher(text).matches();
    }
}
