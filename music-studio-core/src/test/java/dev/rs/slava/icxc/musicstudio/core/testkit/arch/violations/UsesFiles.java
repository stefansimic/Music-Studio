package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import java.nio.file.Files;
import java.nio.file.Path;

/** Deliberate violation of A1/A2 for the rules' self-test. Never used by production code. */
public final class UsesFiles {
    public boolean exists(Path path) {
        return Files.exists(path);
    }
}
