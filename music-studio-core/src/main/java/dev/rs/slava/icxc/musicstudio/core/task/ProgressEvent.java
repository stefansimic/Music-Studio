package dev.rs.slava.icxc.musicstudio.core.task;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A progress snapshot.
 *
 * @param phase             e.g. "list", "read", "plan", "write"; not blank
 * @param done              units done; ≥ 0
 * @param total             total units, ≥ done, or -1 if unknown
 * @param current           the file being worked on; not null, absolute if present
 * @param currentBytes      bytes of the current file done; ≥ 0
 * @param currentBytesTotal bytes of the current file, ≥ currentBytes, or -1 if unknown
 */
public record ProgressEvent(String phase, long done, long total, Optional<Path> current,
                            long currentBytes, long currentBytesTotal) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if {@code phase} or {@code current} is null
     * @throws IllegalArgumentException if a counter is out of range or {@code current} is relative
     */
    public ProgressEvent {
        Require.nonBlank(phase, "phase");
        Require.notNegative(done, "done");
        Require.that(total == -1 || total >= done, "total must be -1 or >= done (%d) but was %d", done, total);
        Require.nonNull(current, "current");
        current.ifPresent(path -> Require.absolute(path, "current"));
        Require.notNegative(currentBytes, "currentBytes");
        Require.that(currentBytesTotal == -1 || currentBytesTotal >= currentBytes,
                "currentBytesTotal must be -1 or >= currentBytes (%d) but was %d", currentBytes, currentBytesTotal);
    }

    /**
     * @param phase not blank
     * @param done  ≥ 0
     * @param total ≥ done or -1
     * @return an event without current file
     */
    public static ProgressEvent of(String phase, long done, long total) {
        return new ProgressEvent(phase, done, total, Optional.empty(), 0, -1);
    }

    /**
     * @param phase   not blank
     * @param done    ≥ 0
     * @param total   ≥ done or -1
     * @param current the current file; not null, absolute
     * @return an event for a file
     */
    public static ProgressEvent of(String phase, long done, long total, Path current) {
        Require.nonNull(current, "current");
        return new ProgressEvent(phase, done, total, Optional.of(current), 0, -1);
    }
}
