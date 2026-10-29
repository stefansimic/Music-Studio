package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Field changes relative to the current content of the tag slot. Fields that are in neither set nor clear stay as
 * they are.
 *
 * @param set   fields that get these values; not null
 * @param clear fields that are removed; not null; disjoint from {@code set}
 */
public record FieldChanges(Map<Field, FieldValue> set, Set<Field> clear) {

    public FieldChanges {
        Require.noNullEntries(set, "set");
        Require.noNullElements(clear, "clear");
        set.forEach((field, value) -> Require.that(value.type() == field.type(),
                "value type %s does not match field %s", value.type(), field));
        Require.that(Collections.disjoint(set.keySet(), clear), "a field cannot be set and cleared at once");
        EnumMap<Field, FieldValue> setCopy = new EnumMap<>(Field.class);
        setCopy.putAll(set);
        EnumSet<Field> clearCopy = EnumSet.noneOf(Field.class);
        clearCopy.addAll(clear);
        set = Collections.unmodifiableMap(setCopy);
        clear = Collections.unmodifiableSet(clearCopy);
    }

    /** @return changes that change nothing; never null */
    public static FieldChanges none() {
        return new FieldChanges(Map.of(), Set.of());
    }

    /**
     * Computes what has to change to turn {@code current} into {@code desired}, restricted to writable fields.
     *
     * @param current  content of the slot now; not null
     * @param desired  content the slot should have; not null
     * @param writable fields the writer can store; not null
     * @return a field is in {@code set} if desired has a different value, in {@code clear} if only current has one;
     *         never null
     * @throws NullPointerException if an argument is null
     */
    public static FieldChanges between(MediaMetadata current, MediaMetadata desired, Set<Field> writable) {
        Require.nonNull(current, "current");
        Require.nonNull(desired, "desired");
        Require.noNullElements(writable, "writable");
        Map<Field, FieldValue> set = new EnumMap<>(Field.class);
        Set<Field> clear = EnumSet.noneOf(Field.class);
        for (Field field : Field.values()) {
            if (!writable.contains(field)) {
                continue;
            }
            var wanted = desired.get(field);
            var existing = current.get(field);
            if (wanted.isPresent() && !wanted.equals(existing)) {
                set.put(field, wanted.get());
            } else if (wanted.isEmpty() && existing.isPresent()) {
                clear.add(field);
            }
        }
        return new FieldChanges(set, clear);
    }

    /** @return true if nothing is set or cleared */
    public boolean isEmpty() {
        return set.isEmpty() && clear.isEmpty();
    }

    /**
     * @param current content of the slot now; not null
     * @return one {@link FieldChange} per changed field, in field order; never null
     * @throws NullPointerException if {@code current} is null
     */
    public List<FieldChange> describe(MediaMetadata current) {
        Require.nonNull(current, "current");
        List<FieldChange> changes = new ArrayList<>();
        for (Field field : Field.values()) {
            if (set.containsKey(field)) {
                changes.add(new FieldChange(field, current.get(field), Optional.of(set.get(field))));
            } else if (clear.contains(field) && current.get(field).isPresent()) {
                changes.add(new FieldChange(field, current.get(field), Optional.empty()));
            }
        }
        return List.copyOf(changes);
    }

    /**
     * @param current content of the slot now; not null
     * @return {@code current} with these changes applied; never null
     * @throws NullPointerException if {@code current} is null
     */
    public MediaMetadata applyTo(MediaMetadata current) {
        Require.nonNull(current, "current");
        MediaMetadata result = current;
        for (Field field : clear) {
            result = result.without(field);
        }
        for (Map.Entry<Field, FieldValue> entry : set.entrySet()) {
            result = result.with(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
