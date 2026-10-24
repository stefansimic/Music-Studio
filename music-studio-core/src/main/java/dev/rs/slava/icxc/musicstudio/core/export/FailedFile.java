package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A file whose write was attempted and failed. A failed file leaves no target behind.
 *
 * @param source absolute; not null
 * @param target the intended target; not null
 * @param error  what went wrong; not null
 */
public record FailedFile(Path source, Optional<Path> target, Issue error) {

    /** @throws NullPointerException if a component is null; IllegalArgumentException if a path is relative */
    public FailedFile {
        Require.absolute(source, "source");
        Require.nonNull(target, "target");
        target.ifPresent(t -> Require.absolute(t, "target"));
        Require.nonNull(error, "error");
    }
}
