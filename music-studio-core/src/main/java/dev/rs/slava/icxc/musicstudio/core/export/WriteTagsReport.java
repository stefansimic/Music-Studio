package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.util.List;

/**
 * The result of applying a plan.
 *
 * @param status   how the run ended; not null
 * @param outcomes one entry per handled item in plan order; after a cancel only the items handled before; not null
 * @param issues   plan-wide issues (copied from a rejected plan); not null
 */
public record WriteTagsReport(WriteStatus status, List<WriteOutcome> outcomes, List<Issue> issues) {

    public WriteTagsReport {
        Require.nonNull(status, "status");
        Require.noNullElements(outcomes, "outcomes");
        Require.noNullElements(issues, "issues");
        outcomes = List.copyOf(outcomes);
        issues = List.copyOf(issues);
    }

    /** @return the number of files written */
    public long written() {
        return outcomes.stream().filter(o -> o instanceof WriteOutcome.Written).count();
    }

    /** @return the number of files that failed */
    public long failed() {
        return outcomes.stream().filter(o -> o instanceof WriteOutcome.Failed).count();
    }

    /** @return the number of files skipped */
    public long skipped() {
        return outcomes.stream().filter(o -> o instanceof WriteOutcome.Skipped).count();
    }
}
