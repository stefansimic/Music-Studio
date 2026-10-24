package dev.rs.slava.icxc.musicstudio.core.model;

/** The value type of a {@link Field}; decides which {@link FieldValue} variant it holds. */
public enum FieldType {
    /** {@link FieldValue.Text} */
    TEXT,
    /** {@link FieldValue.YearValue} */
    YEAR,
    /** {@link FieldValue.Position} */
    POSITION,
    /** {@link FieldValue.Genres} */
    GENRES
}
