package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedAction;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedFile;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.tag.SlotChange;
import dev.rs.slava.icxc.musicstudio.core.tag.ValueChange;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;

/**
 * The export dry-run as text: per file {@code source → target}, per tag slot the version change and one line per
 * changed value ({@code before → after}), then issues ({@code !}), global issues and a summary.
 */
public final class TablePlanRenderer implements PlanRenderer {

    private static final String NONE = "(none)";

    private final PrintWriter out;
    private final Path root;
    private final boolean verbose;

    /**
     * @param out     receives the text; not null
     * @param root    the scanned folder; absolute, not null
     * @param verbose true = also show INFO issues
     */
    public TablePlanRenderer(PrintWriter out, Path root, boolean verbose) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
        this.verbose = verbose;
    }

    @Override
    public void render(ExportPlan plan, List<UnreadableFile> unreadable) {
        Require.nonNull(plan, "plan");
        Require.noNullElements(unreadable, "unreadable");
        for (PlannedFile file : plan.files()) {
            String source = Text.oneLine(Rows.relative(root, file.source()));
            if (file.action() == PlannedAction.SKIP) {
                out.println("SKIP " + source + ": " + reasons(file.issues()));
                continue;
            }
            out.println(source + " → " + Text.oneLine(file.target().orElseThrow().toString()));
            for (SlotChange slot : file.slotChanges()) {
                out.println("  " + slotLine(slot));
                for (ValueChange value : slot.values()) {
                    out.println("    " + Text.fit(value.field().placeholder(), 12) + " " + show(value.field(), value.before())
                            + " → " + show(value.field(), value.after()));
                }
            }
            issues(file.issues());
        }
        if (!plan.issues().isEmpty()) {
            out.println();
            issues(plan.issues());
        }
        if (!unreadable.isEmpty()) {
            out.println();
            out.println("Unreadable (not processed):");
            for (UnreadableFile file : unreadable) {
                out.println("  " + Text.oneLine(Rows.relative(root, file.path())) + "  "
                        + Text.oneLine(file.error().message()));
            }
        }
        out.println();
        out.println("%d file%s: %d to write, %d skipped".formatted(plan.files().size(),
                plan.files().size() == 1 ? "" : "s", plan.writeCount(), plan.skipCount()));
    }

    private static String slotLine(SlotChange slot) {
        Optional<String> before = slot.before().map(dev.rs.slava.icxc.musicstudio.core.model.TagVersion::label);
        Optional<String> after = slot.after().map(dev.rs.slava.icxc.musicstudio.core.model.TagVersion::label);
        if (before.isPresent() && after.isPresent()) {
            if (!before.equals(after)) {
                return before.get() + " → " + after.get();
            }
            return after.get() + (slot.values().isEmpty() ? " (kept, unchanged)" : "");
        }
        return after.map(label -> label + " (new)").orElseGet(() -> before.orElse("?") + " (removed)");
    }

    private static String show(Field field, Optional<FieldValue> value) {
        return value.map(v -> new MediaMetadata(Map.of(field, v)).display(field).orElse(NONE))
                .map(Text::oneLine).orElse(NONE);
    }

    private static String reasons(List<Issue> issues) {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).map(i -> Text.oneLine(i.message()))
                .reduce((a, b) -> a + "; " + b).orElse("skipped");
    }

    private void issues(List<Issue> issues) {
        for (Issue issue : issues) {
            if (issue.severity() == Severity.INFO && !verbose) {
                continue;
            }
            out.println("  ! " + issue.severity().name().toLowerCase(Locale.ROOT) + " " + issue.code()
                    + issue.field().map(f -> " " + f.placeholder()).orElse("") + ": " + Text.oneLine(issue.message()));
        }
    }
}
