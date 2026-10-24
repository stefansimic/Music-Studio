package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.export.ExportedFile;
import dev.rs.slava.icxc.musicstudio.core.export.FailedFile;
import dev.rs.slava.icxc.musicstudio.core.export.SkippedFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The export result as text. Lists every created file and directory: until the undo journal exists (story 004)
 * this is the user's list of what to remove to undo the export.
 */
public final class TableReportRenderer implements ReportRenderer {

    private final PrintWriter out;
    private final Path root;

    /**
     * @param out  receives the text; not null
     * @param root the scanned folder; absolute, not null
     */
    public TableReportRenderer(PrintWriter out, Path root) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void render(ExportReport report) {
        Require.nonNull(report, "report");
        if (!report.createdDirectories().isEmpty()) {
            out.println("Created folders:");
            report.createdDirectories().forEach(d -> out.println("  " + Text.oneLine(d.toString())));
        }
        if (!report.written().isEmpty()) {
            out.println("Written files:");
            for (ExportedFile file : report.written()) {
                out.println("  " + Text.oneLine(file.target().toString()) + "  (from "
                        + Text.oneLine(Rows.relative(root, file.source())) + ")");
            }
        }
        for (FailedFile file : report.failed()) {
            out.println("FAILED " + Text.oneLine(Rows.relative(root, file.source())) + ": "
                    + Text.oneLine(file.error().message()));
        }
        for (SkippedFile file : report.skipped()) {
            out.println("SKIPPED " + Text.oneLine(Rows.relative(root, file.source())) + ": "
                    + Text.oneLine(file.reasons().getFirst().message()));
        }
        report.issues().forEach(i -> out.println("! " + i.severity().name().toLowerCase(Locale.ROOT) + " "
                + i.code() + ": " + Text.oneLine(i.message())));
        out.println("%s: %d written, %d skipped, %d failed".formatted(report.status(), report.written().size(),
                report.skipped().size(), report.failed().size()));
    }
}
