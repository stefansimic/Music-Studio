package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.nio.file.Path;

/**
 * One file to write: the original, the new file, and the metadata the new file should hold.
 *
 * @param source  absolute path of the original, which is never modified; not null
 * @param target  absolute path of the new file; must not exist; not null
 * @param desired the complete metadata the new file should hold (fields it lacks are cleared); not null
 */
public record WriteItem(Path source, Path target, MediaMetadata desired) {

    public WriteItem {
        Require.absolute(source, "source");
        Require.absolute(target, "target");
        Require.nonNull(desired, "desired");
    }
}
