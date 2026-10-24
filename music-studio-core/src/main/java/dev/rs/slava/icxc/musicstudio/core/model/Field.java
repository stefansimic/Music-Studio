package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Optional;

/**
 * A metadata field as the user means it – never a frame ID. Adapters map fields to their format's slots.
 * The placeholder name is the field's name in the pattern language.
 */
public enum Field {
    /** Track title (ID3v2 TIT2). */
    TITLE("title", FieldType.TEXT),
    /** Track artist (TPE1). */
    ARTIST("artist", FieldType.TEXT),
    /** Album artist (TPE2); drives album grouping on phones and players. */
    ALBUM_ARTIST("albumartist", FieldType.TEXT),
    /** Album (TALB). */
    ALBUM("album", FieldType.TEXT),
    /** Release year (TYER / TDRC). */
    YEAR("year", FieldType.YEAR),
    /** Track number and optional total (TRCK). */
    TRACK("track", FieldType.POSITION),
    /** Disc number and optional total (TPOS). */
    DISC("disc", FieldType.POSITION),
    /** Genres, primary first (TCON). */
    GENRE("genre", FieldType.GENRES),
    /** Comment (COMM); the only multi-line field. */
    COMMENT("comment", FieldType.TEXT);

    private final String placeholder;
    private final FieldType type;

    Field(String placeholder, FieldType type) {
        this.placeholder = placeholder;
        this.type = type;
    }

    /** @return the pattern-language placeholder name; lower case, not blank */
    public String placeholder() {
        return placeholder;
    }

    /** @return the value type; not null */
    public FieldType type() {
        return type;
    }

    /** @return true if values may contain line breaks (only {@link #COMMENT}) */
    public boolean multiLine() {
        return this == COMMENT;
    }

    /**
     * Looks a field up by its placeholder name (exact, lower case).
     *
     * @param name the placeholder name; not null
     * @return the field, or empty if no field has this placeholder
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
