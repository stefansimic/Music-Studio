package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deliberate violation of A1/A2 (I/O in core), used by {@code ArchitectureRulesSelfTest}. */
public final class UsesFiles {

    private UsesFiles() {
    }

    static boolean exists(Path path) throws IOException {
        return Files.size(path) > 0;
    }
}
