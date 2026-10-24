package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV (CRLF line ends, header row, fields quoted when needed). Columns: path, the tag fields, cover,
 * duration, bitrate, tags, conflicts (field names), differs (the v1 values of conflicting fields), issues (read
 * warnings/errors of a readable file), error (why a file is unreadable). stdout carries only the CSV; skipped
 * entries go to stderr as {@code skipped: <reason> <path>}, errors of a failed scan as {@code error: <message>}.
 */
public final class CsvScanRenderer implements ScanRenderer {

    private static final String CRLF = "\r\n";

    private final PrintWriter out;
    private final PrintWriter err;
    private final Path root;

    /**
     * @param out  receives the CSV; not null
     * @param err  receives skipped entries and errors; not null
     * @param root the scanned folder; absolute, not null
     */
    public CsvScanRenderer(PrintWriter out, PrintWriter err, Path root) {
        this.out = Require.nonNull(out, "out");
        this.err = Require.nonNull(err, "err");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void begin() {
        List<String> header = new ArrayList<>();
        header.add("path");
        for (Field field : Rows.FIELDS) {
            header.add(field.placeholder());
        }
        header.addAll(List.of("cover", "duration", "bitrate", "tags", "conflicts", "differs", "issues", "error"));
        row(header);
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        List<String> row = new ArrayList<>();
        row.add(Rows.relative(root, item.path()));
        switch (item) {
            case ScannedFile file -> {
                for (Field field : Rows.FIELDS) {
                    row.add(file.tags().effective().display(field).orElse(""));
                }
                row.addAll(List.of(Rows.cover(file.tags()), Rows.duration(file.tags()), Rows.bitrate(file.tags()),
                        Rows.versions(file.tags()),
                        file.conflicts().stream().map(Field::placeholder).collect(Collectors.joining(";")),
                        Rows.differs(file),
                        file.tags().issues().stream().map(i -> i.message()).collect(Collectors.joining("; ")), ""));
            }
            case UnreadableFile unreadable -> {
                for (int i = 0; i < Rows.FIELDS.length + 7; i++) {
                    row.add("");
                }
                row.add(unreadable.error().message());
            }
        }
        row(row);
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        for (SkippedEntry entry : summary.skipped()) {
            err.println("skipped: " + Rows.reason(entry.reason()) + " " + Rows.relative(root, entry.path()));
        }
        summary.issues().forEach(issue -> err.println(
                (summary.status() == ScanStatus.FAILED ? "error: " : "warning: ") + issue.message()));
        if (summary.status() == ScanStatus.CANCELLED) {
            err.println("cancelled");
        }
    }

    private void row(List<String> values) {
        out.print(values.stream().map(CsvScanRenderer::field).collect(Collectors.joining(",")) + CRLF);
    }

    static String field(String value) {
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
