package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedFile;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.tag.SlotChange;
import dev.rs.slava.icxc.musicstudio.core.tag.ValueChange;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The export dry-run as one JSON document mirroring the plan records. */
public final class JsonPlanRenderer implements PlanRenderer {

    private final PrintWriter out;
    private final Path root;

    /**
     * @param out  receives the JSON; not null
     * @param root the scanned folder; absolute, not null
     */
    public JsonPlanRenderer(PrintWriter out, Path root) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void render(ExportPlan plan, List<UnreadableFile> unreadable) {
        Require.nonNull(plan, "plan");
        Require.noNullElements(unreadable, "unreadable");
        out.println("{\"files\":" + Json.array(plan.files().stream().map(this::file).toList())
                + ",\"unreadable\":" + Json.array(unreadable.stream().map(u -> "{\"path\":"
                + Json.string(Rows.relative(root, u.path())) + ",\"message\":" + Json.string(u.error().message())
                + "}").toList())
                + ",\"issues\":" + Json.issues(plan.issues())
                + ",\"summary\":{\"files\":" + plan.files().size() + ",\"write\":" + plan.writeCount()
                + ",\"skip\":" + plan.skipCount() + ",\"executable\":" + plan.executable() + "}}");
    }

    private String file(PlannedFile file) {
        return "{\"source\":" + Json.string(Rows.relative(root, file.source())) + ",\"target\":"
                + file.target().map(t -> Json.string(t.toString())).orElse("null") + ",\"action\":"
                + Json.string(file.action().name().toLowerCase(Locale.ROOT)) + ",\"slots\":"
                + Json.array(file.slotChanges().stream().map(JsonPlanRenderer::slot).toList()) + ",\"issues\":"
                + Json.issues(file.issues()) + "}";
    }

    private static String slot(SlotChange slot) {
        return "{\"family\":" + Json.string(slot.family().name().toLowerCase(Locale.ROOT)) + ",\"before\":"
                + version(slot.before()) + ",\"after\":" + version(slot.after()) + ",\"values\":"
                + Json.array(slot.values().stream().map(JsonPlanRenderer::value).toList()) + "}";
    }

    private static String version(Optional<TagVersion> version) {
        return version.map(v -> Json.string(v.label())).orElse("null");
    }

    private static String value(ValueChange change) {
        return "{\"field\":" + Json.string(change.field().placeholder()) + ",\"before\":" + json(change.before())
                + ",\"after\":" + json(change.after()) + "}";
    }

    private static String json(Optional<FieldValue> value) {
        return value.map(Json::value).orElse("null");
    }

}
