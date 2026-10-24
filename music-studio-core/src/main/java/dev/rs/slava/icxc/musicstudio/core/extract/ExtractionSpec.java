package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How proposed metadata is computed for a batch (architecture §7.2).
 *
 * @param pattern         the input pattern; empty = no extraction from names; not null
 * @param overrides       per field a fixed value or "keep existing"; no nulls; Fixed values fit their field; copied
 * @param mergeMode       how pattern/fixed values combine with existing values; not null
 * @param autoNumbering   track auto-numbering; empty = off; not null
 * @param acceptAmbiguous true = ambiguous matches are exportable with their chosen split (CLI
 *                        {@code --accept-ambiguous})
 */
public record ExtractionSpec(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides,
                             MergeMode mergeMode, Optional<AutoNumbering> autoNumbering, boolean acceptAmbiguous) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component, key or value is null
     * @throws IllegalArgumentException if a Fixed value does not fit its field, or {@link #problems} is not empty
     */
    public ExtractionSpec {
        Require.nonNull(pattern, "pattern");
        Require.noNullEntries(overrides, "overrides");
        Require.nonNull(mergeMode, "mergeMode");
        Require.nonNull(autoNumbering, "autoNumbering");
        overrides.forEach((field, override) -> {
            if (override instanceof FieldOverride.Fixed fixed) {
                Require.that(fixed.value().type() == field.type(), "fixed value of type %s does not fit %s",
                        fixed.value().type(), field);
            }
        });
        List<String> problems = problems(pattern, overrides, autoNumbering);
        Require.that(problems.isEmpty(), "%s", String.join("; ", problems));
        EnumMap<Field, FieldOverride> copy = new EnumMap<>(Field.class);
        copy.putAll(overrides);
        overrides = Collections.unmodifiableMap(copy);
    }

    /**
     * User-level problems of an option combination, so frontends can refuse it before building the spec (CLI: exit
     * 2; GUI: grey out). The constructor throws IllegalArgumentException with these messages.
     *
     * @param pattern       not null
     * @param overrides     not null
     * @param autoNumbering not null
     * @return the problems in English; empty if the combination is valid; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<String> problems(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides,
                                        Optional<AutoNumbering> autoNumbering) {
        Require.nonNull(pattern, "pattern");
        Require.nonNull(overrides, "overrides");
        Require.nonNull(autoNumbering, "autoNumbering");
        List<String> problems = new ArrayList<>();
        if (autoNumbering.isPresent()) {
            if (pattern.map(p -> p.placeholders().contains(Field.TRACK)).orElse(false)) {
                problems.add("track numbers cannot come from both the pattern and auto-numbering");
            }
            if (overrides.containsKey(Field.TRACK)) {
                problems.add("track numbers cannot be fixed or kept and auto-numbered at the same time");
            }
        }
        return List.copyOf(problems);
    }

    /**
     * @param pattern the pattern; not null
     * @return a spec with only the pattern: no overrides, OVERWRITE, no auto-numbering, ambiguous not accepted
     * @throws NullPointerException if {@code pattern} is null
     */
    public static ExtractionSpec of(InputPattern pattern) {
        Require.nonNull(pattern, "pattern");
        return new ExtractionSpec(Optional.of(pattern), Map.of(), MergeMode.OVERWRITE, Optional.empty(), false);
    }
}
