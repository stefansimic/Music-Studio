package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;

/** What to do with one field for all files, instead of (or in addition to) the pattern. */
public sealed interface FieldOverride {

    /**
     * The same value for every file (for example {@code genre=Balkan}). The type must match the field; checked by
     * {@link ExtractionSpec}.
     *
     * @param value not null
     */
    record Fixed(FieldValue value) implements FieldOverride {
        public Fixed {
            Require.nonNull(value, "value");
        }
    }

    /** Keep the value the file has now; the pattern and fixed values are ignored for this field. */
    record KeepExisting() implements FieldOverride {}
}
