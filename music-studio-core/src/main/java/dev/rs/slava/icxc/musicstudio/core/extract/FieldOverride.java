package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;

/** What happens to one field besides the pattern. */
public sealed interface FieldOverride {

    /**
     * The same value for every file (e.g. {@code genre=Balkan}); wins over a pattern value.
     *
     * @param value the value; not null; its type must fit the field (checked by {@link ExtractionSpec})
     */
    record Fixed(FieldValue value) implements FieldOverride {

        /**
         * Validates the value.
         *
         * @throws NullPointerException if {@code value} is null
         */
        public Fixed {
            Require.nonNull(value, "value");
        }
    }

    /** Keep the existing tag value; the pattern and fixed values are ignored for this field. */
    record KeepExisting() implements FieldOverride {
    }
}
