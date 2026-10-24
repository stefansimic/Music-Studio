package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.time.Instant;
import java.util.List;

/**
 * The dry-run result: exactly what {@link ApplyExportUseCase} would do. Creating a plan changes nothing.
 *
 * @param createdAt when the plan was made; not null
 * @param spec      the export specification; not null
 * @param options   the tag write options; not null
 * @param files     one entry per proposal, in order; not null
 * @param issues    global issues (unsupported options, output inside the source); not null
 */
public record ExportPlan(Instant createdAt, ExportSpec spec, TagWriteOptions options,
                         List<PlannedFile> files, List<Issue> issues) {

    /** @throws NullPointerException if a component or element is null */
    public ExportPlan {
        Require.nonNull(createdAt, "createdAt");
        Require.nonNull(spec, "spec");
        Require.nonNull(options, "options");
        Require.noNullElements(files, "files");
        Require.noNullElements(issues, "issues");
        files = List.copyOf(files);
        issues = List.copyOf(issues);
    }

    /** @return true if there is no global ERROR and at least one file will be written */
    public boolean executable() {
        return issues.stream().noneMatch(i -> i.severity() == Severity.ERROR) && writeCount() > 0;
    }

    /** @return the number of WRITE files */
    public int writeCount() {
        return (int) files.stream().filter(f -> f.action() == PlannedAction.WRITE).count();
    }

    /** @return the number of SKIP files */
    public int skipCount() {
        return files.size() - writeCount();
    }
}
