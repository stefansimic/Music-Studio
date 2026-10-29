package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One JSON document, streamed: {@code {"items": [ ... ], "summary": { ... }}}. Every item has {@code path},
 * {@code status}, {@code format}, {@code effective}, {@code layers}, {@code conflicts}, {@code stream},
 * {@code cover} and {@code issues}; an unreadable file has empty values and an error issue. Field values are typed:
 * text as string, year as number, track and disc as {@code {"number": n, "total": t}}, genre as array of strings.
 */
public final class JsonScanRenderer implements ScanRenderer {

    private final PrintWriter out;
    private boolean first = true;

    /** @param out where the document goes; not null */
    public JsonScanRenderer(PrintWriter out) {
        this.out = Require.nonNull(out, "out");
    }

    @Override
    public void begin(ScanRequest request) {
        Require.nonNull(request, "request");
        out.print("{\"items\": [");
        out.flush();
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        out.print(first ? "\n  " : ",\n  ");
        first = false;
        out.print(switch (item) {
            case ScannedFile file -> scanned(file);
            case UnreadableFile unreadable -> unreadable(unreadable);
        });
        out.flush();
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        out.print(first ? "" : "\n");
        out.println("], \"summary\": " + summary(summary) + "}");
        out.flush();
    }

    private static String scanned(ScannedFile file) {
        List<String> layers = new ArrayList<>();
        for (TagLayer layer : file.tags().layers()) {
            layers.add("{\"version\": " + Json.quote(layer.version().label()) + ", \"fields\": "
                    + fields(layer.metadata()) + "}");
        }
        List<String> conflicts = new ArrayList<>();
        for (Field field : Field.values()) {
            if (file.conflicts().contains(field)) {
                conflicts.add(Json.quote(field.placeholder()));
            }
        }
        return "{\"path\": " + Json.quote(file.path().toString())
                + ", \"status\": \"ok\""
                + ", \"format\": " + Json.quote(file.format().name())
                + ", \"fingerprint\": {\"sizeBytes\": " + file.fingerprint().sizeBytes()
                + ", \"lastModified\": " + Json.quote(file.fingerprint().lastModified().toString()) + "}"
                + ", \"effective\": " + fields(file.tags().effective())
                + ", \"layers\": [" + String.join(", ", layers) + "]"
                + ", \"conflicts\": [" + String.join(", ", conflicts) + "]"
                + ", \"stream\": " + file.tags().stream().map(JsonScanRenderer::stream).orElse("null")
                + ", \"cover\": " + file.tags().cover().map(JsonScanRenderer::cover).orElse("null")
                + ", \"issues\": " + issues(file.tags().issues()) + "}";
    }

    private static String unreadable(UnreadableFile file) {
        return "{\"path\": " + Json.quote(file.path().toString())
                + ", \"status\": \"error\""
                + ", \"format\": " + Json.quote(file.format().name())
                + ", \"fingerprint\": null, \"effective\": {}, \"layers\": [], \"conflicts\": []"
                + ", \"stream\": null, \"cover\": null"
                + ", \"issues\": " + issues(List.of(file.error())) + "}";
    }

    static String fields(MediaMetadata metadata) {
        List<String> entries = new ArrayList<>();
        for (Field field : Field.values()) {
            metadata.get(field).ifPresent(value -> entries.add(Json.quote(field.placeholder()) + ": " + value(value)));
        }
        return "{" + String.join(", ", entries) + "}";
    }

    static String value(FieldValue value) {
        return switch (value) {
            case FieldValue.Text text -> Json.quote(text.value());
            case FieldValue.YearValue year -> Integer.toString(year.year());
            case FieldValue.Position position -> "{\"number\": " + position.number()
                    + position.total().stream().mapToObj(total -> ", \"total\": " + total).findFirst().orElse("") + "}";
            case FieldValue.Genres genres -> "["
                    + genres.values().stream().map(genre -> Json.quote(genre.name())).collect(Collectors.joining(", "))
                    + "]";
        };
    }

    private static String stream(StreamInfo stream) {
        return "{\"durationMs\": " + stream.duration().toMillis() + ", \"bitrateKbps\": " + stream.bitrateKbps()
                + ", \"variableBitrate\": " + stream.variableBitrate() + "}";
    }

    private static String cover(CoverInfo cover) {
        return "{\"type\": " + Json.quote(cover.type().name()) + ", \"mimeType\": " + Json.quote(cover.mimeType())
                + ", \"sizeBytes\": " + cover.sizeBytes() + "}";
    }

    static String issues(List<Issue> issues) {
        List<String> entries = new ArrayList<>();
        for (Issue issue : issues) {
            entries.add("{\"severity\": " + Json.quote(issue.severity().name())
                    + ", \"code\": " + Json.quote(issue.code().name())
                    + ", \"message\": " + Json.quote(issue.message())
                    + issue.field().map(field -> ", \"field\": " + Json.quote(field.placeholder())).orElse("")
                    + issue.file().map(file -> ", \"file\": " + Json.quote(file.toString())).orElse("") + "}");
        }
        return "[" + String.join(", ", entries) + "]";
    }

    private static String summary(ScanSummary summary) {
        List<String> ignored = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : summary.ignoredByExtension().entrySet()) {
            ignored.add(Json.quote(entry.getKey()) + ": " + entry.getValue());
        }
        List<String> skipped = new ArrayList<>();
        for (SkippedEntry entry : summary.skipped()) {
            skipped.add("{\"path\": " + Json.quote(entry.path().toString()) + ", \"reason\": "
                    + Json.quote(entry.reason().name()) + "}");
        }
        return "{\"root\": " + Json.quote(summary.root().toString())
                + ", \"status\": " + Json.quote(summary.status().name())
                + ", \"found\": " + summary.found()
                + ", \"read\": " + summary.read()
                + ", \"failed\": " + summary.failed()
                + ", \"ignoredFiles\": " + summary.ignoredFiles()
                + ", \"ignoredByExtension\": {" + String.join(", ", ignored) + "}"
                + ", \"skipped\": [" + String.join(", ", skipped) + "]"
                + ", \"issues\": " + issues(summary.issues()) + "}";
    }
}
