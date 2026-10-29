package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Result of matching one path against an input pattern.
 *
 * @param kind             not null
 * @param values           typed values of the chosen split; empty for NO_MATCH; not null
 * @param raw              the captured (NFC, stripped) texts of the chosen split, same keys as {@code values}; not null
 * @param alternativeCount number of other complete splits, counted up to 1 (so 1 means "at least one other split");
 *                         0 unless AMBIGUOUS
 * @param reason           why there is no match (names the failing part and the pattern segment); present iff NO_MATCH
 */
public record MatchResult(MatchKind kind, Map<Field, FieldValue> values, Map<Field, String> raw,
                          int alternativeCount, Optional<String> reason) {

    public MatchResult {
        Require.nonNull(kind, "kind");
        Require.noNullEntries(values, "values");
        Require.noNullEntries(raw, "raw");
        Require.that(values.keySet().equals(raw.keySet()), "values and raw must have the same fields");
        Require.that(alternativeCount >= 0 && (alternativeCount == 0 || kind == MatchKind.AMBIGUOUS),
                "alternativeCount is only for AMBIGUOUS");
        Require.nonNull(reason, "reason");
        Require.that(reason.isPresent() == (kind == MatchKind.NO_MATCH), "a reason is given exactly for NO_MATCH");
        Require.that(kind != MatchKind.NO_MATCH || values.isEmpty(), "NO_MATCH has no values");
        EnumMap<Field, FieldValue> v = new EnumMap<>(Field.class);
        v.putAll(values);
        EnumMap<Field, String> r = new EnumMap<>(Field.class);
        r.putAll(raw);
        values = Collections.unmodifiableMap(v);
        raw = Collections.unmodifiableMap(r);
    }

    /** @return a NO_MATCH result with this reason */
    public static MatchResult noMatch(String reason) {
        return new MatchResult(MatchKind.NO_MATCH, Map.of(), Map.of(), 0, Optional.of(reason));
    }
}
