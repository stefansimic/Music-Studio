package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;

/**
 * A file or directory deliberately not scanned.
 *
 * @param path   absolute path; not null
 * @param reason why it was skipped; not null
 */
public record SkippedEntry(Path path, SkipReason reason) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if {@code path} is relative
     */
    public SkippedEntry {
        Require.absolute(path, "path");
        Require.nonNull(reason, "reason");
    }
}
