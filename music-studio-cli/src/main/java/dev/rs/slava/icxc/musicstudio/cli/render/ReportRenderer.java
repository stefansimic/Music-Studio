package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;

/** Renders an {@link ExportReport}. */
public interface ReportRenderer {

    /** @param report what the export did; not null */
    void render(ExportReport report);
}
