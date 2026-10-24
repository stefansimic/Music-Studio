package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;

/** The export result as one JSON document. */
public final class JsonReportRenderer implements ReportRenderer {

    private final PrintWriter out;
    private final Path root;

    /**
     * @param out  receives the JSON; not null
     * @param root the scanned folder; absolute, not null
     */
    public JsonReportRenderer(PrintWriter out, Path root) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void render(ExportReport report) {
        Require.nonNull(report, "report");
        out.println("{\"status\":" + Json.string(report.status().name().toLowerCase(Locale.ROOT))
                + ",\"outputRoot\":" + Json.string(report.outputRoot().toString())
                + ",\"createdDirectories\":" + Json.array(report.createdDirectories().stream()
                        .map(d -> Json.string(d.toString())).toList())
                + ",\"written\":" + Json.array(report.written().stream().map(f -> "{\"source\":"
                        + Json.string(Rows.relative(root, f.source())) + ",\"target\":"
                        + Json.string(f.target().toString()) + ",\"issues\":" + Json.issues(f.issues()) + "}")
                        .toList())
                + ",\"skipped\":" + Json.array(report.skipped().stream().map(f -> "{\"source\":"
                        + Json.string(Rows.relative(root, f.source())) + ",\"issues\":" + Json.issues(f.reasons())
                        + "}").toList())
                + ",\"failed\":" + Json.array(report.failed().stream().map(f -> "{\"source\":"
                        + Json.string(Rows.relative(root, f.source())) + ",\"issue\":" + Json.issue(f.error())
                        + "}").toList())
                + ",\"issues\":" + Json.issues(report.issues()) + ",\"elapsedMillis\":"
                + report.elapsed().toMillis() + "}");
    }

}
