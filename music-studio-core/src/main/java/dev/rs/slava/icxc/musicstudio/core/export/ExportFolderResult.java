package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link ExportFolderUseCase}. Stages that did not run are empty: with {@code optionIssues} nothing was
 * scanned; with a failed scan there is no plan; a dry-run or a non-executable plan has no report.
 *
 * @param optionIssues ERROR issues for write options the writer cannot fulfil; not null
 * @param scan         the scan summary; not null (empty if the options were rejected)
 * @param unreadable   files that could not be read and are not part of the plan; not null
 * @param plan         the export plan; not null
 * @param report       what the export did; not null (present only after an apply)
 */
public record ExportFolderResult(List<Issue> optionIssues, Optional<ScanSummary> scan,
                                 List<UnreadableFile> unreadable, Optional<ExportPlan> plan,
                                 Optional<ExportReport> report) {

    /** @throws NullPointerException if a component or element is null */
    public ExportFolderResult {
        Require.noNullElements(optionIssues, "optionIssues");
        Require.nonNull(scan, "scan");
        Require.noNullElements(unreadable, "unreadable");
        Require.nonNull(plan, "plan");
        Require.nonNull(report, "report");
        Require.that(report.isEmpty() || plan.isPresent(), "a report needs a plan");
        optionIssues = List.copyOf(optionIssues);
        unreadable = List.copyOf(unreadable);
    }
}
