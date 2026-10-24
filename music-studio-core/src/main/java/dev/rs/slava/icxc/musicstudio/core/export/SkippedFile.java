package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.nio.file.Path;
import java.util.List;

/**
 * A file that was not written.
 *
 * @param source  absolute; not null
 * @param reasons why; not null, non-empty
 */
public record SkippedFile(Path source, List<Issue> reasons) {

    /** @throws NullPointerException if a component or element is null; IllegalArgumentException if no reason */
    public SkippedFile {
        Require.absolute(source, "source");
        Require.noNullElements(reasons, "reasons");
        Require.that(!reasons.isEmpty(), "reasons must not be empty");
        reasons = List.copyOf(reasons);
    }
}
