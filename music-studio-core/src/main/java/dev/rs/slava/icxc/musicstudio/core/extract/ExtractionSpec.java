package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * How to derive metadata for a batch.
 *
 * @param pattern         the input pattern, empty if only fixed values, keep-existing or numbering are used; not null
 * @param overrides       per field a fixed value or "keep"; not null, no nulls; a fixed value's type matches its field
 * @param mergeMode       what happens to fields that already have a value; not null
 * @param autoNumbering   track numbering by sort order; not null
 * @param acceptAmbiguous true: ambiguous matches are used (status AMBIGUOUS_ACCEPTED); false: they are shown but not
 *                        exportable
 */
public record ExtractionSpec(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides, MergeMode mergeMode,
                             Optional<AutoNumbering> autoNumbering, boolean acceptAmbiguous) {

    public ExtractionSpec {
        Require.nonNull(pattern, "pattern");
        Require.noNullEntries(overrides, "overrides");
        Require.nonNull(mergeMode, "mergeMode");
        Require.nonNull(autoNumbering, "autoNumbering");
        overrides.forEach((field, override) -> {
            if (override instanceof FieldOverride.Fixed fixed) {
                Require.that(fixed.value().type() == field.type(), "fixed value type %s does not match %s",
                        fixed.value().type(), field);
            }
        });
        EnumMap<Field, FieldOverride> copy = new EnumMap<>(Field.class);
        copy.putAll(overrides);
        overrides = Collections.unmodifiableMap(copy);
    }

    /**
     * @param pattern the input pattern; not null
     * @return a spec with only this pattern: overwrite mode, no overrides, no numbering, ambiguous not accepted
     */
    public static ExtractionSpec of(InputPattern pattern) {
        Require.nonNull(pattern, "pattern");
        return new ExtractionSpec(Optional.of(pattern), Map.of(), MergeMode.OVERWRITE, Optional.empty(), false);
    }

    /** @return true if the spec asks for nothing: no pattern, overrides or numbering */
    public boolean isEmpty() {
        return pattern.isEmpty() && overrides.isEmpty() && autoNumbering.isEmpty();
    }
}
