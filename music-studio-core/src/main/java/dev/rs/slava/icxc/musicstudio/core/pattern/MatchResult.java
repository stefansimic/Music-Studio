package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The result of {@link PathMatcher#match}.
 *
 * <ul>
 *   <li>MATCHED: {@code values}/{@code raw} for every placeholder; {@code alternative} and {@code reason} empty.</li>
 *   <li>AMBIGUOUS: {@code values}/{@code raw} = the chosen (non-greedy, left-to-right) split; {@code alternative}
 *       = the raw values of the greedy split for the fields where it differs (non-empty); {@code reason} empty.</li>
 *   <li>NO_MATCH: {@code values}, {@code raw}, {@code alternative} empty; {@code reason} present.</li>
 * </ul>
 *
 * @param kind        how it matched; not null
 * @param values      typed values per placeholder field; same keys as {@code raw}; copied
 * @param raw         the stripped captured text per placeholder field (NFC); copied
 * @param alternative the other split's raw values (AMBIGUOUS only); keys ⊆ raw keys; copied
 * @param reason      English, why nothing matched (NO_MATCH only); not null
 */
public record MatchResult(MatchKind kind, Map<Field, FieldValue> values, Map<Field, String> raw,
                          Map<Field, String> alternative, Optional<String> reason) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or a map entry is null
     * @throws IllegalArgumentException if the components do not fit {@code kind}
     */
    public MatchResult {
        Require.nonNull(kind, "kind");
        Require.noNullEntries(values, "values");
        Require.noNullEntries(raw, "raw");
        Require.noNullEntries(alternative, "alternative");
        Require.nonNull(reason, "reason");
        Require.that(values.keySet().equals(raw.keySet()), "values and raw must have the same fields");
        Require.that(raw.keySet().containsAll(alternative.keySet()), "alternative fields must be placeholders");
        switch (kind) {
            case MATCHED -> Require.that(alternative.isEmpty() && reason.isEmpty(), "MATCHED has no alternative");
            case AMBIGUOUS -> Require.that(!alternative.isEmpty() && reason.isEmpty(),
                    "AMBIGUOUS needs an alternative");
            case NO_MATCH -> Require.that(values.isEmpty() && alternative.isEmpty() && reason.isPresent(),
                    "NO_MATCH has a reason and no values");
        }
        values = copy(values);
        raw = copy(raw);
        alternative = copy(alternative);
    }

    private static <V> Map<Field, V> copy(Map<Field, V> map) {
        EnumMap<Field, V> copy = new EnumMap<>(Field.class);
        copy.putAll(map);
        return Collections.unmodifiableMap(copy);
    }

    /**
     * @param reason why nothing matched; not blank
     * @return a NO_MATCH result
     */
    public static MatchResult noMatch(String reason) {
        Require.nonBlank(reason, "reason");
        return new MatchResult(MatchKind.NO_MATCH, Map.of(), Map.of(), Map.of(), Optional.of(reason));
    }
}
