package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Format-neutral metadata of one tag layer or of a proposal. Only fields that have a value are present.
 *
 * <p>Invariants: no null keys or values; {@code value.type() == field.type()}; values of single-line fields contain
 * no line break. The map is an unmodifiable copy in {@link Field} order.
 *
 * <p><b>Thread-safety:</b> immutable.
 *
 * @param values the field values; not null, no null keys/values; copied defensively
 */
public record MediaMetadata(Map<Field, FieldValue> values) {

    private static final MediaMetadata EMPTY = new MediaMetadata(Map.of());

    /**
     * Validates and copies the map.
     *
     * @throws NullPointerException     if {@code values} or a key/value is null
     * @throws IllegalArgumentException if a value has the wrong type for its field, or a single-line field contains
     *                                  a line break
     */
    public MediaMetadata {
        Require.noNullEntries(values, "values");
        EnumMap<Field, FieldValue> copy = new EnumMap<>(Field.class);
        for (Map.Entry<Field, FieldValue> entry : values.entrySet()) {
            check(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), entry.getValue());
        }
        values = Collections.unmodifiableMap(copy);
    }

    private static void check(Field field, FieldValue value) {
        Require.that(value.type() == field.type(), "value of type %s does not fit field %s (%s)",
                value.type(), field, field.type());
        if (!field.multiLine() && value instanceof FieldValue.Text text) {
            Require.that(TextRules.isSingleLine(text.value()), "field %s must be a single line", field);
        }
    }

    /** @return metadata without any value; never null */
    public static MediaMetadata empty() {
        return EMPTY;
    }

    /**
     * @param field the field; not null
     * @return the value, or empty if the field has none
     * @throws NullPointerException if {@code field} is null
     */
    public Optional<FieldValue> get(Field field) {
        Require.nonNull(field, "field");
        return Optional.ofNullable(values.get(field));
    }

    /**
     * @param field the field; not null
     * @return the canonical display text of the value, or empty if the field has none
     * @throws NullPointerException if {@code field} is null
     */
    public Optional<String> display(Field field) {
        Require.nonNull(field, "field");
        return get(field).map(FieldValue::display);
    }

    /**
     * Returns a copy with {@code field} set to {@code value}.
     *
     * @param field the field; not null
     * @param value the value; not null, {@code value.type() == field.type()}
     * @return the new metadata; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if the value does not fit the field
     */
    public MediaMetadata with(Field field, FieldValue value) {
        Require.nonNull(field, "field");
        Require.nonNull(value, "value");
        check(field, value);
        EnumMap<Field, FieldValue> copy = copy();
        copy.put(field, value);
        return new MediaMetadata(copy);
    }

    /**
     * Returns a copy without a value for {@code field}.
     *
     * @param field the field; not null
     * @return the new metadata (this if the field had no value); never null
     * @throws NullPointerException if {@code field} is null
     */
    public MediaMetadata without(Field field) {
        Require.nonNull(field, "field");
        if (!values.containsKey(field)) {
            return this;
        }
        EnumMap<Field, FieldValue> copy = copy();
        copy.remove(field);
        return new MediaMetadata(copy);
    }

    /** @return true if no field has a value */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @param other the metadata to compare with; not null
     * @return the fields whose presence or value differs between this and {@code other}; unmodifiable, never null
     * @throws NullPointerException if {@code other} is null
     */
    public Set<Field> differingFields(MediaMetadata other) {
        Require.nonNull(other, "other");
        EnumSet<Field> result = EnumSet.noneOf(Field.class);
        for (Field field : Field.values()) {
            if (!Objects.equals(values.get(field), other.values.get(field))) {
                result.add(field);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private EnumMap<Field, FieldValue> copy() {
        EnumMap<Field, FieldValue> copy = new EnumMap<>(Field.class);
        copy.putAll(values);
        return copy;
    }
}
