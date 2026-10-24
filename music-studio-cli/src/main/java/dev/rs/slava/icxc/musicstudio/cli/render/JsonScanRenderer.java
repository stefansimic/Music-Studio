package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Streams one JSON document: {@code {"root": …, "items": [ … ], "summary": { … }}}. Every item has {@code path}
 * (relative to root, "/" separators) and {@code format}; a read file also {@code versions}, {@code effective},
 * {@code layers[]}, {@code conflicts[]}, {@code stream}, {@code cover}, {@code issues[]}; an unreadable file
 * {@code error}. Field keys are the pattern placeholders; years are numbers, positions objects, genres arrays.
 */
public final class JsonScanRenderer implements ScanRenderer {

    private final PrintWriter out;
    private final Path root;
    private boolean first = true;

    /**
     * @param out  receives the document; not null
     * @param root the scanned folder; absolute, not null
     */
    public JsonScanRenderer(PrintWriter out, Path root) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void begin() {
        out.print("{\"root\":" + Json.string(root.toString()) + ",\"items\":[");
    }

    @Override
    public void item(ScanItem item) {
        Require.nonNull(item, "item");
        out.print(first ? "\n" : ",\n");
        first = false;
        StringBuilder json = new StringBuilder("{\"path\":").append(Json.string(Rows.relative(root, item.path())));
        switch (item) {
            case ScannedFile file -> {
                json.append(",\"format\":").append(Json.string(file.format().name().toLowerCase(Locale.ROOT)));
                json.append(",\"versions\":").append(Json.array(file.tags().versions().stream()
                        .map(v -> Json.string(v.label())).toList()));
                json.append(",\"effective\":").append(Json.metadata(file.tags().effective()));
                json.append(",\"layers\":").append(Json.array(file.tags().layers().stream().map(this::layer).toList()));
                json.append(",\"conflicts\":").append(Json.array(file.conflicts().stream()
                        .map(f -> Json.string(f.placeholder())).toList()));
                json.append(",\"stream\":").append(stream(file.tags().stream()));
                json.append(",\"cover\":").append(cover(file.tags().cover()));
                json.append(",\"issues\":").append(Json.issues(file.tags().issues()));
            }
            case UnreadableFile unreadable -> {
                json.append(",\"format\":").append(Json.string(unreadable.format().name().toLowerCase(Locale.ROOT)));
                json.append(",\"error\":").append(Json.issue(unreadable.error()));
            }
        }
        out.print(json.append('}'));
    }

    @Override
    public void end(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        out.print(first ? "]" : "\n]");
        String ignored = summary.ignoredByExtension().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Json.string(e.getKey()) + ":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        String skipped = Json.array(summary.skipped().stream().map(this::skipped).toList());
        out.println(",\"summary\":{\"status\":" + Json.string(summary.status().name().toLowerCase(Locale.ROOT))
                + ",\"found\":" + summary.found() + ",\"read\":" + summary.read() + ",\"failed\":" + summary.failed()
                + ",\"ignored\":" + summary.ignoredFiles() + ",\"ignoredByExtension\":" + ignored
                + ",\"skipped\":" + skipped + ",\"issues\":" + Json.issues(summary.issues()) + "}}");
    }

    private String layer(TagLayer layer) {
        return "{\"version\":" + Json.string(layer.version().label()) + ",\"fields\":" + Json.metadata(layer.metadata())
                + "}";
    }

    private static String stream(Optional<StreamInfo> stream) {
        return stream.map(s -> "{\"durationSeconds\":" + String.format(Locale.ROOT, "%.3f", s.duration().toMillis()
                / 1000.0) + ",\"bitrateKbps\":" + s.bitrateKbps() + ",\"variableBitrate\":" + s.variableBitrate()
                + "}").orElse("null");
    }

    private static String cover(Optional<CoverInfo> cover) {
        return cover.map(c -> "{\"type\":" + Json.string(c.type().name().toLowerCase(Locale.ROOT)) + ",\"mimeType\":"
                + Json.string(c.mimeType()) + ",\"sizeBytes\":" + c.sizeBytes() + "}").orElse("null");
    }

    private String skipped(SkippedEntry entry) {
        return "{\"path\":" + Json.string(Rows.relative(root, entry.path())) + ",\"reason\":"
                + Json.string(entry.reason().name().toLowerCase(Locale.ROOT)) + "}";
    }

}
