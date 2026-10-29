package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Optional;

/**
 * One field where the proposal differs from the file's current tags.
 *
 * @param field  not null
 * @param before the current value, empty if none; not null
 * @param after  the proposed value, empty if the field is cleared; not null; differs from {@code before}
 * @param origin which step set it; not null
 */
public record ProposedChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after,
                             ChangeOrigin origin) {

    public ProposedChange {
        Require.nonNull(field, "field");
        Require.nonNull(before, "before");
        Require.nonNull(after, "after");
        Require.nonNull(origin, "origin");
        Require.that(!before.equals(after), "a change must change the value");
    }
}
