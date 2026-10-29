package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import java.util.List;

/**
 * The dry-run: what apply would do. Producing a plan writes nothing.
 *
 * @param request the request the plan was made for; not null
 * @param items   one entry per request item in request order, or empty if {@code issues} contain an error (the
 *                options cannot be written, so no item was examined); not null
 * @param issues  problems that affect the whole plan, such as an unsupported tag version; not null
 */
public record WriteTagsPlan(WriteTagsRequest request, List<PlannedWrite> items, List<Issue> issues) {

    public WriteTagsPlan {
        Require.nonNull(request, "request");
        Require.noNullElements(items, "items");
        Require.noNullElements(issues, "issues");
        items = List.copyOf(items);
        issues = List.copyOf(issues);
    }

    /** @return true if no plan-wide error exists, so {@code apply} may run */
    public boolean executable() {
        return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
    }
}
