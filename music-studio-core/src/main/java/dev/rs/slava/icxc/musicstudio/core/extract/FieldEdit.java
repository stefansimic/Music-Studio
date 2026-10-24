package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;

/** A manual edit of one field of one file (GUI cell); always wins. */
public sealed interface FieldEdit {

    /**
     * Set the field to a value.
     *
     * @param value not null; its type must fit the field (checked by {@link ManualEdits})
     */
    record Assign(FieldValue value) implements FieldEdit {

        /**
         * Validates the value.
         *
         * @throws NullPointerException if {@code value} is null
         */
        public Assign {
            Require.nonNull(value, "value");
        }
    }

    /** Remove the field's value. */
    record Clear() implements FieldEdit {
    }
}
