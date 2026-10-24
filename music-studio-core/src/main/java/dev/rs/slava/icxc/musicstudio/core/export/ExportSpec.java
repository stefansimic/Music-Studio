package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;

/**
 * Where and how files are exported. Story 003: flat targets {@code outputRoot/<source file name>}.
 *
 * @param outputRoot absolute; not null; need not exist yet
 */
public record ExportSpec(Path outputRoot) {

    /** @throws NullPointerException if outputRoot is null; IllegalArgumentException if it is not absolute */
    public ExportSpec {
        Require.absolute(outputRoot, "outputRoot");
    }
}
