package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Optional;

/**
 * One value of one tag that changes.
 *
 * @param field  the field; not null
 * @param before the value before; not null (empty = absent)
 * @param after  the value after; not null (empty = absent); differs from {@code before}
 */
public record ValueChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if before equals after
     */
    public ValueChange {
        Require.nonNull(field, "field");
        Require.nonNull(before, "before");
        Require.nonNull(after, "after");
        Require.that(!before.equals(after), "a value change must change %s", field);
    }
}
