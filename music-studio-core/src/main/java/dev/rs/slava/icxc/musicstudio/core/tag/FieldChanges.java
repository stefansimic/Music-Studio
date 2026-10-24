package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Field changes relative to the current content of the target tag slot. Fields in neither set stay untouched.
 *
 * @param set   fields to set; values fit their field; no nulls; copied
 * @param clear fields to remove; disjoint from {@code set}; no nulls; copied
 */
public record FieldChanges(Map<Field, FieldValue> set, Set<Field> clear) {

    /** No change. */
    public static final FieldChanges NONE = new FieldChanges(Map.of(), Set.of());

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component, key, value or element is null
     * @throws IllegalArgumentException if a value does not fit its field or a field is in both sets
     */
    public FieldChanges {
        Require.noNullEntries(set, "set");
        Require.noNullElements(clear, "clear");
        set.forEach((field, value) -> Require.that(value.type() == field.type(), "value of type %s does not fit %s",
                value.type(), field));
        for (Field field : clear) {
            Require.that(!set.containsKey(field), "%s is both set and cleared", field);
        }
        EnumMap<Field, FieldValue> setCopy = new EnumMap<>(Field.class);
        setCopy.putAll(set);
        set = Collections.unmodifiableMap(setCopy);
        clear = clear.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(clear));
    }

    /**
     * The changes that turn {@code current} into {@code desired}, restricted to {@code writable} fields.
     *
     * @param current  the target slot's current values; not null
     * @param desired  the wanted values; not null
     * @param writable the fields the writer can write; not null
     * @return the changes; never null
     * @throws NullPointerException if an argument is null
     */
    public static FieldChanges between(MediaMetadata current, MediaMetadata desired, Set<Field> writable) {
        Require.nonNull(current, "current");
        Require.nonNull(desired, "desired");
        Require.noNullElements(writable, "writable");
        Map<Field, FieldValue> set = new EnumMap<>(Field.class);
        Set<Field> clear = EnumSet.noneOf(Field.class);
        for (Field field : current.differingFields(desired)) {
            if (!writable.contains(field)) {
                continue;
            }
            desired.get(field).ifPresentOrElse(value -> set.put(field, value), () -> clear.add(field));
        }
        return new FieldChanges(set, clear);
    }

    /**
     * @param current the slot's current values; not null
     * @return {@code current} with the changes applied; never null
     * @throws NullPointerException if {@code current} is null
     */
    public MediaMetadata applyTo(MediaMetadata current) {
        Require.nonNull(current, "current");
        MediaMetadata result = current;
        for (Map.Entry<Field, FieldValue> entry : set.entrySet()) {
            result = result.with(entry.getKey(), entry.getValue());
        }
        for (Field field : clear) {
            result = result.without(field);
        }
        return result;
    }

    /** @return true if nothing changes */
    public boolean isEmpty() {
        return set.isEmpty() && clear.isEmpty();
    }
}
