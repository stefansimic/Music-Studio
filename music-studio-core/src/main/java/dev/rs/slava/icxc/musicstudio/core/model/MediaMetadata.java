package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Format-neutral metadata of one tag layer or of a proposal. Only fields that have a value are present.
 *
 * @param values no null keys or values; {@code value.type() == field.type()}; single-line unless
 *               {@link Field#multiLine()}. Copied defensively, unmodifiable.
 */
public record MediaMetadata(Map<Field, FieldValue> values) {

    private static final MediaMetadata EMPTY = new MediaMetadata(Map.of());

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
        Require.that(value.type() == field.type(), "%s needs a %s value but got %s", field, field.type(),
                value.type());
        if (value instanceof FieldValue.Text text && !field.multiLine()) {
            Require.that(text.value().indexOf('\n') < 0 && text.value().indexOf('\r') < 0,
                    "%s must be a single line", field);
        }
    }

    /** @return metadata without any field; never null */
    public static MediaMetadata empty() {
        return EMPTY;
    }

    /**
     * @param field field to look up; not null
     * @return the value, empty if the field is absent
     * @throws NullPointerException if {@code field} is null
     */
    public Optional<FieldValue> get(Field field) {
        Require.nonNull(field, "field");
        return Optional.ofNullable(values.get(field));
    }

    /**
     * @param field field to look up; not null
     * @return the display text of the value, empty if the field is absent
     * @throws NullPointerException if {@code field} is null
     */
    public Optional<String> display(Field field) {
        return get(field).map(FieldValue::display);
    }

    /**
     * @param field field to set; not null
     * @param value new value; not null; {@code value.type() == field.type()}
     * @return a copy with the field set
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if the value does not fit the field
     */
    public MediaMetadata with(Field field, FieldValue value) {
        Require.nonNull(field, "field");
        Require.nonNull(value, "value");
        EnumMap<Field, FieldValue> copy = new EnumMap<>(Field.class);
        copy.putAll(values);
        copy.put(field, value);
        return new MediaMetadata(copy);
    }

    /**
     * @param field field to remove; not null
     * @return a copy without the field
     * @throws NullPointerException if {@code field} is null
     */
    public MediaMetadata without(Field field) {
        Require.nonNull(field, "field");
        EnumMap<Field, FieldValue> copy = new EnumMap<>(Field.class);
        copy.putAll(values);
        copy.remove(field);
        return new MediaMetadata(copy);
    }

    /** @return true if no field has a value */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @param other metadata to compare with; not null
     * @return the fields whose presence or value differs between this and {@code other}; never null
     * @throws NullPointerException if {@code other} is null
     */
    public Set<Field> differingFields(MediaMetadata other) {
        Require.nonNull(other, "other");
        Set<Field> result = EnumSet.noneOf(Field.class);
        for (Field field : Field.values()) {
            if (!java.util.Objects.equals(values.get(field), other.values.get(field))) {
                result.add(field);
            }
        }
        return result;
    }
}
