package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A deliberately dumb, reflection-free JSON writer for the write records (spec X03). If a record can be written
 * here with plain accessors, the future web codec can do the same without reflection.
 */
final class WriteJson {

    private WriteJson() {}

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    static <T> String list(List<T> items, Function<T, String> f) {
        return items.stream().map(f).collect(Collectors.joining(",", "[", "]"));
    }

    static <T> String opt(Optional<T> o, Function<T, String> f) {
        return o.map(f).orElse("null");
    }

    static String options(TagWriteOptions o) {
        return "{\"id3v2\":" + q(o.id3v2().name()) + ",\"id3v1\":" + q(o.id3v1().name()) + ",\"v2Encoding\":"
                + q(o.v2Encoding().name()) + ",\"v1TextMode\":" + q(o.v1TextMode().name()) + ",\"v1NonLatinPolicy\":"
                + q(o.v1NonLatinPolicy().name()) + ",\"removeOtherVersions\":" + o.removeOtherVersions() + "}";
    }

    static String value(FieldValue v) {
        return switch (v) {
            case FieldValue.Text t -> "{\"text\":" + q(t.value()) + "}";
            case FieldValue.YearValue y -> "{\"year\":" + y.year() + "}";
            case FieldValue.Position p -> "{\"number\":" + p.number() + ",\"total\":"
                    + (p.total().isPresent() ? p.total().getAsInt() : "null") + "}";
            case FieldValue.Genres g -> "{\"genres\":" + list(g.values(), x -> q(x.name())) + "}";
        };
    }

    static String metadata(MediaMetadata m) {
        return m.values().entrySet().stream().map(e -> q(e.getKey().name()) + ":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    static String issue(Issue i) {
        return "{\"severity\":" + q(i.severity().name()) + ",\"code\":" + q(i.code().name()) + ",\"message\":"
                + q(i.message()) + ",\"file\":" + opt(i.file(), f -> q(f.toString())) + ",\"field\":"
                + opt(i.field(), f -> q(f.name())) + "}";
    }

    static String change(FieldChange c) {
        return "{\"field\":" + q(c.field().name()) + ",\"old\":" + opt(c.oldValue(), WriteJson::value) + ",\"new\":"
                + opt(c.newValue(), WriteJson::value) + "}";
    }

    static String fieldChanges(FieldChanges c) {
        return "{\"set\":" + c.set().entrySet().stream().map(e -> q(e.getKey().name()) + ":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}")) + ",\"clear\":"
                + list(List.copyOf(c.clear()), f -> q(f.name())) + "}";
    }

    static String v1(Id3v1Values v) {
        return "{\"version\":" + q(v.version().name()) + ",\"encoding\":" + q(v.encoding().name()) + ",\"title\":"
                + q(v.title()) + ",\"artist\":" + q(v.artist()) + ",\"album\":" + q(v.album()) + ",\"year\":"
                + (v.year().isPresent() ? v.year().getAsInt() : "null") + ",\"comment\":" + q(v.comment())
                + ",\"track\":" + v.track() + ",\"genre\":" + v.genre() + "}";
    }

    static String writeRequest(TagWriteRequest r) {
        return "{\"format\":" + q(r.format().name()) + ",\"changes\":" + fieldChanges(r.changes()) + ",\"cover\":"
                + q(r.cover() instanceof CoverChange.Keep ? "KEEP" : "?") + ",\"options\":" + options(r.options())
                + ",\"id3v1\":" + opt(r.id3v1(), WriteJson::v1) + "}";
    }

    static String request(WriteTagsRequest r) {
        return "{\"items\":" + list(r.items(), i -> "{\"source\":" + q(i.source().toString()) + ",\"target\":"
                + q(i.target().toString()) + ",\"desired\":" + metadata(i.desired()) + "}") + ",\"options\":"
                + options(r.options()) + "}";
    }

    static String fingerprint(FileFingerprint f) {
        return "{\"sizeBytes\":" + f.sizeBytes() + ",\"lastModified\":" + q(f.lastModified().toString()) + "}";
    }

    static String planned(PlannedWrite p) {
        return "{\"source\":" + q(p.source().toString()) + ",\"target\":" + q(p.target().toString())
                + ",\"format\":" + opt(p.format(), f -> q(f.name())) + ",\"action\":" + q(p.action().name())
                + ",\"sourceFingerprint\":" + opt(p.sourceFingerprint(), WriteJson::fingerprint) + ",\"changes\":"
                + list(p.changes(), WriteJson::change) + ",\"request\":" + opt(p.request(), WriteJson::writeRequest)
                + ",\"expectedV2\":" + metadata(p.expectedV2()) + ",\"issues\":" + list(p.issues(), WriteJson::issue)
                + "}";
    }

    static String plan(WriteTagsPlan p) {
        return "{\"request\":" + request(p.request()) + ",\"items\":" + list(p.items(), WriteJson::planned)
                + ",\"issues\":" + list(p.issues(), WriteJson::issue) + "}";
    }

    static String outcome(WriteOutcome o) {
        return switch (o) {
            case WriteOutcome.Written w -> "{\"type\":\"written\",\"target\":" + q(w.planned().target().toString())
                    + ",\"targetFingerprint\":" + w.targetFingerprint().map(f -> fingerprint(f)).orElse("null") + "}";
            case WriteOutcome.Failed f -> "{\"type\":\"failed\",\"target\":" + q(f.planned().target().toString())
                    + ",\"error\":" + issue(f.error()) + "}";
            case WriteOutcome.Skipped s -> "{\"type\":\"skipped\",\"target\":" + q(s.planned().target().toString())
                    + "}";
        };
    }

    static String report(WriteTagsReport r) {
        return "{\"status\":" + q(r.status().name()) + ",\"outcomes\":" + list(r.outcomes(), WriteJson::outcome)
                + ",\"issues\":" + list(r.issues(), WriteJson::issue) + "}";
    }
}
