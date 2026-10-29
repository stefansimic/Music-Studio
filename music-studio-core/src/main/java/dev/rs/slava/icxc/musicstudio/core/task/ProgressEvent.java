package dev.rs.slava.icxc.musicstudio.core.task;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Progress of an operation phase.
 *
 * @param phase             phase name such as "list", "read", "plan", "write"; not blank
 * @param done              units finished, {@code >= 0}
 * @param total             total units, {@code >= done}, or -1 if unknown
 * @param current           the file being processed, absolute, if any
 * @param currentBytes      bytes finished of the current file, {@code >= 0}
 * @param currentBytesTotal bytes total of the current file, {@code >= currentBytes}, or -1 if unknown
 */
public record ProgressEvent(String phase, long done, long total, Optional<Path> current, long currentBytes,
                            long currentBytesTotal) {
    public ProgressEvent {
        Require.nonBlank(phase, "phase");
        Require.notNegative(done, "done");
        Require.that(total == -1 || total >= done, "total must be -1 or >= done but was %d (done %d)", total, done);
        Require.nonNull(current, "current");
        current.ifPresent(path -> Require.absolute(path, "current"));
        Require.notNegative(currentBytes, "currentBytes");
        Require.that(currentBytesTotal == -1 || currentBytesTotal >= currentBytes,
                "currentBytesTotal must be -1 or >= currentBytes");
    }

    /**
     * @param phase phase name; not blank
     * @param done  units finished
     * @param total units total or -1
     * @return an event without current file
     */
    public static ProgressEvent of(String phase, long done, long total) {
        return new ProgressEvent(phase, done, total, Optional.empty(), 0, -1);
    }

    /**
     * @param phase   phase name; not blank
     * @param done    units finished
     * @param total   units total or -1
     * @param current the file being processed; absolute
     * @return an event for a file
     */
    public static ProgressEvent of(String phase, long done, long total, Path current) {
        return new ProgressEvent(phase, done, total, Optional.of(Require.absolute(current, "current")), 0, -1);
    }
}
