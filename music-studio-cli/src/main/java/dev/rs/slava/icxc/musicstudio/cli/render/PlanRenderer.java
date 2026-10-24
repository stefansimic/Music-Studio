package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.util.List;

/** Renders an {@link ExportPlan} (the dry-run result). */
public interface PlanRenderer {

    /**
     * @param plan       the plan; not null
     * @param unreadable files that could not be read and are not part of the plan; not null
     */
    void render(ExportPlan plan, List<UnreadableFile> unreadable);
}
