package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Human-readable table with fixed column widths, printed row by row while the scan streams. Values of fields where
 * ID3v1 and ID3v2 differ end with "*"; the last column (NOTES) lists the differing v1 values and read warnings.
 * After the table: counts, ignored files, the "Skipped (not scanned)" section and warnings.
 */
public final class TableScanRenderer implements ScanRenderer {

    private record Column(String title, int width) {
    }

    private static final String GAP = "  ";
    private static final Column PATH = new Column("PATH", 36);
    private static final List<Column> FIELD_COLUMNS = Arrays.stream(Rows.FIELDS)
            .map(field -> new Column(Rows.header(field), Rows.width(field))).toList();
    private static final List<Column> TAIL = List.of(
            new Column("COVER", 5), new Column("TIME", 5), new Column("KBPS", 7), new Column("TAGS", 17));

    private final PrintWriter out;
    private final PrintWriter err;
    private final Path root;
    private int rows;

    /**
     * @param out  receives the table; not null
     * @param err  receives the error of a failed scan; not null
     * @param root the scanned folder; paths are shown relative to it; absolute, not null
     */
    public TableScanRenderer(PrintWriter out, PrintWriter err, Path root) {
        this.out = Require.nonNull(out, "out");
        this.err = Require.nonNull(err, "err");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void begin() {
        // the header is printed with the first row, so a failed scan shows only its error
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        if (rows++ == 0) {
            header();
        }
        String path = Text.oneLine(Rows.relative(root, item.path()));
        StringBuilder line = new StringBuilder(Text.fit(path, PATH.width()));
        switch (item) {
            case ScannedFile file -> {
                for (int i = 0; i < FIELD_COLUMNS.size(); i++) {
                    Field field = Rows.FIELDS[i];
                    int width = FIELD_COLUMNS.get(i).width();
                    String value = Rows.value(file, field);
                    line.append(GAP).append(file.conflicts().contains(field)
                            ? Text.fit(Text.cut(value, width - 1) + "*", width)
                            : Text.fit(value, width));
                }
                List<String> tail = List.of(Rows.cover(file.tags()), Rows.duration(file.tags()),
                        Rows.bitrate(file.tags()), Rows.versions(file.tags()));
                for (int i = 0; i < TAIL.size(); i++) {
                    line.append(GAP).append(Text.fit(tail.get(i), TAIL.get(i).width()));
                }
                line.append(GAP).append(Rows.notes(file));
            }
            case UnreadableFile unreadable -> line.append(GAP).append("ERROR: ")
                    .append(Text.oneLine(unreadable.error().message()));
        }
        out.println(line.toString().stripTrailing());
    }

    private void header() {
        List<Column> all = new ArrayList<>();
        all.add(PATH);
        all.addAll(FIELD_COLUMNS);
        all.addAll(TAIL);
        out.println((all.stream().map(c -> Text.fit(c.title(), c.width())).collect(Collectors.joining(GAP))
                + GAP + "NOTES").stripTrailing());
        out.println(all.stream().map(c -> "-".repeat(c.width())).collect(Collectors.joining(GAP))
                + GAP + "-".repeat(12));
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        if (summary.status() == ScanStatus.FAILED) {
            summary.issues().forEach(issue -> err.println("Error: " + Text.oneLine(issue.message())));
            return;
        }
        if (rows > 0) {
            out.println();
        }
        out.println(summary.found() == 0 ? "No MP3 files found."
                : "%d MP3 file%s: %d read, %d unreadable.".formatted(summary.found(),
                        summary.found() == 1 ? "" : "s", summary.read(), summary.failed()));
        if (summary.ignoredFiles() > 0) {
            out.println("Ignored %d other file%s (%s).".formatted(summary.ignoredFiles(),
                    summary.ignoredFiles() == 1 ? "" : "s", ignored(summary.ignoredByExtension())));
        }
        if (!summary.skipped().isEmpty()) {
            out.println("Skipped (not scanned):");
            for (SkippedEntry entry : summary.skipped()) {
                out.println("  " + Text.fit(Rows.reason(entry.reason()), 17) + GAP
                        + Text.oneLine(Rows.relative(root, entry.path())));
            }
        }
        for (Issue issue : summary.issues()) {
            out.println((issue.severity() == Severity.ERROR ? "Error: " : "Warning: ")
                    + Text.oneLine(issue.message()));
        }
        if (summary.status() == ScanStatus.CANCELLED) {
            out.println("Cancelled: %d of %d files shown.".formatted(summary.read() + summary.failed(),
                    summary.found()));
        }
    }

    static String ignored(Map<String, Integer> byExtension) {
        return byExtension.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (e.getKey().isEmpty() ? "no extension" : e.getKey()) + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
