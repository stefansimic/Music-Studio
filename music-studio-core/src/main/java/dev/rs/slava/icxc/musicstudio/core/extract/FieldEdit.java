package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;

/** A manual change of one field of one file (a cell edited in the GUI). */
public sealed interface FieldEdit {

    /**
     * @param value the new value; not null; the type must match the field
     */
    record Set(FieldValue value) implements FieldEdit {
        public Set {
            Require.nonNull(value, "value");
        }
    }

    /** The field is emptied. */
    record Clear() implements FieldEdit {}
}
