package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Optional;

/**
 * One line of a dry-run: a field and how its value changes.
 *
 * @param field    not null
 * @param oldValue the current value, empty if the field has none; not null
 * @param newValue the new value, empty if the field is cleared; not null
 */
public record FieldChange(Field field, Optional<FieldValue> oldValue, Optional<FieldValue> newValue) {

    public FieldChange {
        Require.nonNull(field, "field");
        Require.nonNull(oldValue, "oldValue");
        Require.nonNull(newValue, "newValue");
        Require.that(oldValue.isPresent() || newValue.isPresent(), "a change needs an old or a new value");
        Require.that(oldValue.map(v -> v.type() == field.type()).orElse(true), "old value type does not match %s", field);
        Require.that(newValue.map(v -> v.type() == field.type()).orElse(true), "new value type does not match %s", field);
    }
}
