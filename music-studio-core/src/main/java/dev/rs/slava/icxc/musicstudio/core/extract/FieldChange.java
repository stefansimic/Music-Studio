package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Optional;

/**
 * One field that the proposal changes.
 *
 * @param field  the field; not null
 * @param before the existing value; not null (empty = absent)
 * @param after  the proposed value; not null (empty = cleared); differs from {@code before}
 * @param origin where the new value comes from; not null
 */
public record FieldChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after, ChangeOrigin origin) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if before equals after
     */
    public FieldChange {
        Require.nonNull(field, "field");
        Require.nonNull(before, "before");
        Require.nonNull(after, "after");
        Require.nonNull(origin, "origin");
        Require.that(!before.equals(after), "a change must change the value of %s", field);
    }
}
