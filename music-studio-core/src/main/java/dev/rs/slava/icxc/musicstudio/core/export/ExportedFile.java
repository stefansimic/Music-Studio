package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.nio.file.Path;
import java.util.List;

/**
 * A file that was written and verified.
 *
 * @param source absolute; not null
 * @param target absolute; the new file; not null
 * @param issues warnings from the plan; not null
 */
public record ExportedFile(Path source, Path target, List<Issue> issues) {

    /** @throws NullPointerException if a component or element is null; IllegalArgumentException if a path is relative */
    public ExportedFile {
        Require.absolute(source, "source");
        Require.absolute(target, "target");
        Require.noNullElements(issues, "issues");
        issues = List.copyOf(issues);
    }
}
