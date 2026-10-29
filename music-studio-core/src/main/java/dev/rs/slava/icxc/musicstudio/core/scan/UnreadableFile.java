package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import java.nio.file.Path;

/**
 * A file that could not be read. The scan continues after it.
 *
 * @param path   absolute; not null
 * @param format the format selected by the file name; not null
 * @param error  an ERROR issue (READ_FAILED) with a human-readable message; not null
 */
public record UnreadableFile(Path path, MediaFormat format, Issue error) implements ScanItem {
    public UnreadableFile {
        Require.absolute(path, "path");
        Require.nonNull(format, "format");
        Require.nonNull(error, "error");
        Require.that(error.severity() == Severity.ERROR, "error must have severity ERROR");
    }
}
