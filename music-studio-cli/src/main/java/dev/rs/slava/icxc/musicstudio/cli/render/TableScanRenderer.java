package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fixed-width table, written row by row while the scan runs. Values that are too long end with an ellipsis. A
 * value marked with {@code *} differs between the ID3v1 and the ID3v2 tag (the shown value is the ID3v2 one); the last
 * column names the fields.
 */
public final class TableScanRenderer implements ScanRenderer {

    private record Column(String header, int width, Function<ScannedFile, String> value) {}

    private static String shown(ScannedFile file, Field field) {
        String text = file.tags().effective().display(field).orElse("");
        return text.isEmpty() || !file.conflicts().contains(field) ? text : text + "*";
    }

    /** Width of the last column: its value is neither padded nor cut. */
    private static final int UNBOUNDED = -1;

    private static final List<Column> COLUMNS = List.of(
            new Column("TITLE", 28, f -> shown(f, Field.TITLE)),
            new Column("ARTIST", 22, f -> shown(f, Field.ARTIST)),
            new Column("ALBUM ARTIST", 18, f -> shown(f, Field.ALBUM_ARTIST)),
            new Column("ALBUM", 24, f -> shown(f, Field.ALBUM)),
            new Column("TRACK", 6, f -> shown(f, Field.TRACK)),
            new Column("DISC", 5, f -> shown(f, Field.DISC)),
            new Column("YEAR", 5, f -> shown(f, Field.YEAR)),
            new Column("GENRE", 14, f -> shown(f, Field.GENRE)),
            new Column("COMMENT", 20, f -> shown(f, Field.COMMENT)),
            new Column("COVER", 5, f -> ScanFormat.cover(f.tags())),
            new Column("TIME", 5, f -> f.tags().stream().map(s -> Text.minutesSeconds(s.duration())).orElse("")),
            new Column("KBPS", 4, f -> f.tags().stream().map(s -> Integer.toString(s.bitrateKbps())).orElse("")),
            new Column("TAGS", 17, f -> ScanFormat.tags(f.tags())),
            new Column("DIFFERS", UNBOUNDED, f -> ScanFormat.differs(f.conflicts())));

    private static final int PATH_WIDTH = 40;
    private static final String GAP = "  ";

    private final PrintWriter out;
    private Path root;

    /** @param out where the table goes; not null */
    public TableScanRenderer(PrintWriter out) {
        this.out = Require.nonNull(out, "out");
    }

    @Override
    public void begin(ScanRequest request) {
        Require.nonNull(request, "request");
        root = request.root();
        List<String> header = new ArrayList<>();
        List<String> rule = new ArrayList<>();
        header.add(Text.fit("PATH", PATH_WIDTH));
        rule.add("-".repeat(PATH_WIDTH));
        for (Column column : COLUMNS) {
            header.add(column.width() == UNBOUNDED ? column.header() : Text.fit(column.header(), column.width()));
            rule.add("-".repeat(column.width() == UNBOUNDED ? column.header().length() : column.width()));
        }
        line(header);
        line(rule);
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        List<String> cells = new ArrayList<>();
        cells.add(Text.fit(relative(item.path()), PATH_WIDTH));
        switch (item) {
            case ScannedFile file -> {
                for (Column column : COLUMNS) {
                    String value = column.value().apply(file);
                    cells.add(column.width() == UNBOUNDED ? Text.oneLine(value) : Text.fit(value, column.width()));
                }
            }
            case UnreadableFile unreadable -> cells.add("ERROR: " + Text.oneLine(unreadable.error().message()));
        }
        line(cells);
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        out.println();
        out.println(summary.status() + ": " + summary.found() + " files found, " + summary.read() + " read, "
                + summary.failed() + " unreadable, " + summary.ignoredFiles() + " ignored" + ignored(summary));
        if (!summary.skipped().isEmpty()) {
            out.println();
            out.println("Skipped (not scanned):");
            for (SkippedEntry entry : summary.skipped()) {
                out.println("  " + entry.reason().name().toLowerCase(java.util.Locale.ROOT) + "  "
                        + Text.oneLine(relative(entry.path())));
            }
        }
        if (!summary.issues().isEmpty()) {
            out.println();
            out.println("Problems:");
            for (Issue issue : summary.issues()) {
                out.println("  " + issue.severity() + "  " + Text.oneLine(issue.message()));
            }
        }
        out.flush();
    }

    private static String ignored(ScanSummary summary) {
        if (summary.ignoredByExtension().isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(" (");
        for (Map.Entry<String, Integer> entry : summary.ignoredByExtension().entrySet()) {
            if (text.length() > 2) {
                text.append(", ");
            }
            text.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.append(')').toString();
    }

    private String relative(Path path) {
        return root != null && path.startsWith(root) ? root.relativize(path).toString() : path.toString();
    }

    private void line(List<String> cells) {
        out.println(String.join(GAP, cells).stripTrailing());
    }
}
