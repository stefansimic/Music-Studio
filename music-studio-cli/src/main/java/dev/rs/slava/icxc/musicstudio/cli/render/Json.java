package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal JSON writing (RFC 8259) shared by the renderers. No library, no reflection. */
final class Json {

    private Json() {
    }

    /** @return {@code value} as a quoted JSON string, or {@code null} for null */
    static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ' || c == 0x7F) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Field values keyed by placeholder name: text → string, year → number, position → object, genres → array. */
    static String metadata(MediaMetadata metadata) {
        return metadata.values().entrySet().stream()
                .map(e -> string(e.getKey().placeholder()) + ":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    static String value(FieldValue value) {
        return switch (value) {
            case FieldValue.Text text -> string(text.value());
            case FieldValue.YearValue year -> Integer.toString(year.year());
            case FieldValue.Position position -> "{\"number\":" + position.number() + ",\"total\":"
                    + (position.total().isPresent() ? Integer.toString(position.total().getAsInt()) : "null") + "}";
            case FieldValue.Genres genres -> array(genres.values().stream().map(Genre::name).map(Json::string).toList());
        };
    }

    /** Raw strings keyed by placeholder name. */
    static String strings(Map<Field, String> values) {
        return values.entrySet().stream()
                .map(e -> string(e.getKey().placeholder()) + ":" + string(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    static String issues(List<Issue> issues) {
        return array(issues.stream().map(Json::issue).toList());
    }

    static String issue(Issue issue) {
        return "{\"severity\":" + string(issue.severity().name().toLowerCase(Locale.ROOT)) + ",\"code\":"
                + string(issue.code().name()) + ",\"field\":"
                + issue.field().map(Field::placeholder).map(Json::string).orElse("null") + ",\"message\":"
                + string(issue.message()) + "}";
    }

    static String array(List<String> elements) {
        return elements.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
