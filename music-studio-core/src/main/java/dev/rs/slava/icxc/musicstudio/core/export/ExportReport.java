package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What an export run did. Lists every created file and directory, so the user can see (and remove) them.
 *
 * <p>For a report of a plan: {@code written + skipped + failed == plan.files().size()}.
 *
 * @param status             how the run ended; not null
 * @param outputRoot         absolute; not null
 * @param written            files written and verified, in plan order; not null
 * @param skipped            files not written (plan skips, cancelled or not attempted); not null
 * @param failed             files whose write failed; not null
 * @param createdDirectories directories this run created, outermost first; not null
 * @param issues             run-level issues; not null
 * @param elapsed            wall-clock duration; not null, not negative
 */
public record ExportReport(ExportStatus status, Path outputRoot, List<ExportedFile> written,
                           List<SkippedFile> skipped, List<FailedFile> failed, List<Path> createdDirectories,
                           List<Issue> issues, Duration elapsed) {

    /** @throws NullPointerException if a component or element is null; IllegalArgumentException if violated */
    public ExportReport {
        Require.nonNull(status, "status");
        Require.absolute(outputRoot, "outputRoot");
        Require.noNullElements(written, "written");
        Require.noNullElements(skipped, "skipped");
        Require.noNullElements(failed, "failed");
        Require.noNullElements(createdDirectories, "createdDirectories");
        Require.noNullElements(issues, "issues");
        Require.nonNull(elapsed, "elapsed");
        Require.that(!elapsed.isNegative(), "elapsed must not be negative");
        written = List.copyOf(written);
        skipped = List.copyOf(skipped);
        failed = List.copyOf(failed);
        createdDirectories = List.copyOf(createdDirectories);
        issues = List.copyOf(issues);
    }
}
