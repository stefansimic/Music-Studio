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
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV: one header row, then one row per file, CRLF line endings, UTF-8. Values with a comma, quote or line
 * break are quoted. Paths are absolute. What was skipped and any problems go to the error stream as
 * {@code skipped: <reason> <path>} lines, so standard output stays a clean CSV.
 */
public final class CsvScanRenderer implements ScanRenderer {

    private static final String EOL = "\r\n";
    private static final List<Field> FIELDS = List.of(Field.TITLE, Field.ARTIST, Field.ALBUM_ARTIST, Field.ALBUM,
            Field.TRACK, Field.DISC, Field.YEAR, Field.GENRE, Field.COMMENT);

    private final PrintWriter out;
    private final PrintWriter err;

    /**
     * @param out the CSV; not null
     * @param err skipped entries and problems; not null
     */
    public CsvScanRenderer(PrintWriter out, PrintWriter err) {
        this.out = Require.nonNull(out, "out");
        this.err = Require.nonNull(err, "err");
    }

    @Override
    public void begin(ScanRequest request) {
        Require.nonNull(request, "request");
        List<String> header = new ArrayList<>();
        header.add("path");
        FIELDS.forEach(field -> header.add(field.placeholder()));
        header.addAll(List.of("cover", "duration_ms", "bitrate_kbps", "tags", "differs", "error"));
        row(header);
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        List<String> cells = new ArrayList<>();
        cells.add(item.path().toString());
        switch (item) {
            case ScannedFile file -> {
                for (Field field : FIELDS) {
                    cells.add(file.tags().effective().display(field).orElse(""));
                }
                cells.add(ScanFormat.cover(file.tags()));
                cells.add(file.tags().stream().map(s -> Long.toString(s.duration().toMillis())).orElse(""));
                cells.add(file.tags().stream().map(s -> Integer.toString(s.bitrateKbps())).orElse(""));
                cells.add(ScanFormat.tags(file.tags()));
                cells.add(ScanFormat.differs(file.conflicts()));
                cells.add("");
            }
            case UnreadableFile unreadable -> {
                for (int i = 0; i < FIELDS.size() + 5; i++) {
                    cells.add("");
                }
                cells.add(unreadable.error().message());
            }
        }
        row(cells);
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        out.flush();
        for (SkippedEntry entry : summary.skipped()) {
            err.println("skipped: " + entry.reason().name().toLowerCase(java.util.Locale.ROOT) + " " + entry.path());
        }
        for (Issue issue : summary.issues()) {
            err.println(issue.severity().name().toLowerCase(java.util.Locale.ROOT) + ": " + Text.oneLine(issue.message()));
        }
        err.println(summary.status().name().toLowerCase(java.util.Locale.ROOT) + ": " + summary.found() + " found, "
                + summary.read() + " read, " + summary.failed() + " unreadable, " + summary.ignoredFiles() + " ignored");
        err.flush();
    }

    private void row(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells.get(i)));
        }
        out.print(line.append(EOL));
        out.flush();
    }

    static String escape(String value) {
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
