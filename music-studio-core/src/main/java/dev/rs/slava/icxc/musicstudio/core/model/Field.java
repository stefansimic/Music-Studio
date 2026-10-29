package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Optional;

/** What the user means by a metadata field, never a frame ID. The placeholder is the pattern-language name. */
public enum Field {
    TITLE("title", FieldType.TEXT),
    ARTIST("artist", FieldType.TEXT),
    ALBUM_ARTIST("albumartist", FieldType.TEXT),
    ALBUM("album", FieldType.TEXT),
    YEAR("year", FieldType.YEAR),
    TRACK("track", FieldType.POSITION),
    DISC("disc", FieldType.POSITION),
    GENRE("genre", FieldType.GENRES),
    COMMENT("comment", FieldType.TEXT, true);

    private final String placeholder;
    private final FieldType type;
    private final boolean multiLine;

    Field(String placeholder, FieldType type) {
        this(placeholder, type, false);
    }

    Field(String placeholder, FieldType type, boolean multiLine) {
        this.placeholder = placeholder;
        this.type = type;
        this.multiLine = multiLine;
    }

    /** @return pattern-language name, lower-case; never null */
    public String placeholder() {
        return placeholder;
    }

    /** @return type of the value this field holds; never null */
    public FieldType type() {
        return type;
    }

    /** @return true only for fields whose text may contain line breaks (COMMENT) */
    public boolean multiLine() {
        return multiLine;
    }

    /**
     * @param name placeholder name, exact and lower-case; not null
     * @return the field, empty if unknown
     * @throws NullPointerException if {@code name} is null
     */
    public static Optional<Field> byPlaceholder(String name) {
        Require.nonNull(name, "name");
        for (Field field : values()) {
            if (field.placeholder.equals(name)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
