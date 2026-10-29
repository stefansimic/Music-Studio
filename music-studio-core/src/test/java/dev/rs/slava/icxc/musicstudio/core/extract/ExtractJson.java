package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.MatchResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternError;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.Token;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A deliberately dumb, reflection-free JSON writer for the extract and pattern records (spec X03): plain accessors and
 * explicit {@code type} tags for the sealed interfaces. If it can be written here, a web codec can do the same.
 */
final class ExtractJson {

    private ExtractJson() {}

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        s.codePoints().forEach(c -> {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", c));
                    } else {
                        sb.appendCodePoint(c);
                    }
                }
            }
        });
        return sb.append('"').toString();
    }

    static <T> String list(List<T> items, Function<T, String> f) {
        return items.stream().map(f).collect(Collectors.joining(",", "[", "]"));
    }

    static <T> String opt(Optional<T> o, Function<T, String> f) {
        return o.map(f).orElse("null");
    }

    static <K, V> String map(Map<K, V> map, Function<K, String> key, Function<V, String> value) {
        return map.entrySet().stream().map(e -> q(key.apply(e.getKey())) + ":" + value.apply(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
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
        return map(m.values(), Field::name, ExtractJson::value);
    }

    static String token(Token t) {
        return switch (t) {
            case Token.Literal l -> "{\"type\":\"LITERAL\",\"text\":" + q(l.text()) + "}";
            case Token.Placeholder p -> "{\"type\":\"PLACEHOLDER\",\"field\":" + q(p.field().name()) + ",\"padWidth\":"
                    + (p.padWidth().isPresent() ? p.padWidth().getAsInt() : "null") + "}";
            case Token.Wildcard w -> "{\"type\":\"WILDCARD\"}";
        };
    }

    static String pattern(InputPattern p) {
        return "{\"source\":" + q(p.source()) + ",\"segments\":"
                + list(p.segments(), s -> list(s.tokens(), ExtractJson::token)) + "}";
    }

    static String override(FieldOverride o) {
        return switch (o) {
            case FieldOverride.Fixed f -> "{\"type\":\"FIXED\",\"value\":" + value(f.value()) + "}";
            case FieldOverride.KeepExisting k -> "{\"type\":\"KEEP_EXISTING\"}";
        };
    }

    static String edit(FieldEdit e) {
        return switch (e) {
            case FieldEdit.Set s -> "{\"type\":\"SET\",\"value\":" + value(s.value()) + "}";
            case FieldEdit.Clear c -> "{\"type\":\"CLEAR\"}";
        };
    }

    static String spec(ExtractionSpec s) {
        return "{\"pattern\":" + opt(s.pattern(), ExtractJson::pattern) + ",\"overrides\":"
                + map(s.overrides(), Field::name, ExtractJson::override) + ",\"mergeMode\":" + q(s.mergeMode().name())
                + ",\"autoNumbering\":" + opt(s.autoNumbering(),
                a -> "{\"order\":" + q(a.order().name()) + ",\"setTotal\":" + a.setTotal() + "}")
                + ",\"acceptAmbiguous\":" + s.acceptAmbiguous() + "}";
    }

    static String edits(ManualEdits e) {
        return "{\"edits\":" + map(e.edits(), p -> p.toString(),
                fields -> map(fields, Field::name, ExtractJson::edit)) + ",\"acceptedAmbiguous\":"
                + list(e.acceptedAmbiguous().stream().map(Object::toString).sorted().toList(), ExtractJson::q) + "}";
    }

    static String file(ScannedFile f) {
        return "{\"path\":" + q(f.path().toString()) + ",\"format\":" + q(f.format().name()) + ",\"sizeBytes\":"
                + f.fingerprint().sizeBytes() + ",\"lastModified\":" + q(f.fingerprint().lastModified().toString())
                + ",\"tags\":" + metadata(f.tags().effective()) + "}";
    }

    static String request(ProposeRequest r) {
        return "{\"files\":" + list(r.files(), ExtractJson::file) + ",\"spec\":" + spec(r.spec()) + ",\"edits\":"
                + edits(r.edits()) + "}";
    }

    static String issue(Issue i) {
        return "{\"severity\":" + q(i.severity().name()) + ",\"code\":" + q(i.code().name()) + ",\"message\":"
                + q(i.message()) + ",\"file\":" + opt(i.file(), p -> q(p.toString())) + ",\"field\":"
                + opt(i.field(), f -> q(f.name())) + "}";
    }

    static String change(ProposedChange c) {
        return "{\"field\":" + q(c.field().name()) + ",\"before\":" + opt(c.before(), ExtractJson::value) + ",\"after\":"
                + opt(c.after(), ExtractJson::value) + ",\"origin\":" + q(c.origin().name()) + "}";
    }

    static String proposal(Proposal p) {
        return "{\"file\":" + file(p.file()) + ",\"status\":" + q(p.status().name()) + ",\"matchDetail\":"
                + opt(p.matchDetail(), ExtractJson::q) + ",\"extracted\":" + map(p.extracted(), Field::name, ExtractJson::q)
                + ",\"proposed\":" + metadata(p.proposed()) + ",\"changes\":" + list(p.changes(), ExtractJson::change)
                + ",\"issues\":" + list(p.issues(), ExtractJson::issue) + "}";
    }

    static String proposalSet(ProposalSet s) {
        return "{\"proposals\":" + list(s.proposals(), ExtractJson::proposal) + ",\"matched\":" + s.matched()
                + ",\"ambiguous\":" + s.ambiguous() + ",\"noMatch\":" + s.noMatch() + "}";
    }

    static String match(MatchResult r) {
        return "{\"kind\":" + q(r.kind().name()) + ",\"values\":" + metadata(new MediaMetadata(r.values()))
                + ",\"raw\":" + map(r.raw(), Field::name, ExtractJson::q) + ",\"alternativeCount\":" + r.alternativeCount()
                + ",\"reason\":" + opt(r.reason(), ExtractJson::q) + "}";
    }

    static String invalid(PatternParseResult.Invalid<?> r) {
        return "{\"errors\":" + list(r.errors(), (PatternError e) -> "{\"column\":" + e.column() + ",\"message\":"
                + q(e.message()) + "}") + "}";
    }
}
