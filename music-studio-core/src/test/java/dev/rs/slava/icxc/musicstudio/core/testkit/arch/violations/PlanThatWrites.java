package dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import java.nio.file.Path;

/** Deliberate violation of A13 (a planner creating directories), used by {@code ArchitectureRulesSelfTest}. */
public final class PlanThatWrites {

    private PlanThatWrites() {
    }

    static void plan(ExportTarget target, Path directory) throws MediaAccessException {
        target.createDirectories(directory);
    }
}
