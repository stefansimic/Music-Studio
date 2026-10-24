package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.nio.file.Path;

/**
 * A file that could not be read (corrupt, no permission, vanished).
 *
 * @param path   absolute path; not null
 * @param format the format detected by extension; not null
 * @param error  the reason, an ERROR issue with code READ_FAILED; not null
 */
public record UnreadableFile(Path path, MediaFormat format, Issue error) implements ScanItem {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if {@code path} is relative
     */
    public UnreadableFile {
        Require.absolute(path, "path");
        Require.nonNull(format, "format");
        Require.nonNull(error, "error");
    }
}
