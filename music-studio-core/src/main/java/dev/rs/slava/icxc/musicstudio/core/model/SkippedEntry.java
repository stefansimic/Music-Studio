package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;

/**
 * A file or directory deliberately not scanned.
 *
 * @param path   absolute; not null
 * @param reason not null
 */
public record SkippedEntry(Path path, SkipReason reason) {
    public SkippedEntry {
        Require.absolute(path, "path");
        Require.nonNull(reason, "reason");
    }
}
