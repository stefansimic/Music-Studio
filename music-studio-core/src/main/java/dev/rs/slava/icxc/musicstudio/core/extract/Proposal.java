package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The proposed final metadata of one file.
 *
 * <p>Invariants: {@code changes} lists exactly the fields where {@code proposed} differs from {@link #existing()},
 * in Field order, with matching before/after. NO_MATCH ⇒ proposed equals existing, no changes, matchDetail present.
 * NO_PATTERN ⇒ extracted empty. {@code alternative} is non-empty only for AMBIGUOUS/AMBIGUOUS_ACCEPTED.
 *
 * @param file        the scanned file; not null
 * @param status      the pattern status; not null
 * @param matchDetail why the pattern did not match (NO_MATCH); not null
 * @param extracted   the raw extracted text per placeholder (also for fields not used); copied
 * @param alternative the other split's raw values (ambiguous only); copied
 * @param proposed    the proposed metadata; not null
 * @param changes     the changed fields; no nulls; copied
 * @param issues      problems of this file (NO_MATCH, AMBIGUOUS_MATCH, POSITION_EXCEEDS_TOTAL); copied
 */
public record Proposal(ScannedFile file, MatchStatus status, Optional<String> matchDetail,
                       Map<Field, String> extracted, Map<Field, String> alternative, MediaMetadata proposed,
                       List<FieldChange> changes, List<Issue> issues) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or element is null
     * @throws IllegalArgumentException if an invariant is violated
     */
    public Proposal {
        Require.nonNull(file, "file");
        Require.nonNull(status, "status");
        Require.nonNull(matchDetail, "matchDetail");
        Require.noNullEntries(extracted, "extracted");
        Require.noNullEntries(alternative, "alternative");
        Require.nonNull(proposed, "proposed");
        Require.noNullElements(changes, "changes");
        Require.noNullElements(issues, "issues");
        MediaMetadata existing = file.tags().effective();
        Set<Field> differing = existing.differingFields(proposed);
        List<Field> changed = new ArrayList<>();
        for (FieldChange change : changes) {
            changed.add(change.field());
            Require.that(change.before().equals(existing.get(change.field()))
                    && change.after().equals(proposed.get(change.field())), "change of %s does not match",
                    change.field());
        }
        Require.that(changed.equals(new ArrayList<>(differing)), "changes must list exactly the differing fields");
        if (status == MatchStatus.NO_MATCH) {
            Require.that(proposed.equals(existing) && matchDetail.isPresent(),
                    "NO_MATCH keeps the existing metadata and has a detail");
        }
        if (status == MatchStatus.NO_PATTERN) {
            Require.that(extracted.isEmpty(), "NO_PATTERN has no extracted values");
        }
        Require.that(alternative.isEmpty() || status == MatchStatus.AMBIGUOUS
                || status == MatchStatus.AMBIGUOUS_ACCEPTED, "only ambiguous proposals have an alternative");
        extracted = copy(extracted);
        alternative = copy(alternative);
        changes = List.copyOf(changes);
        issues = List.copyOf(issues);
    }

    private static Map<Field, String> copy(Map<Field, String> map) {
        EnumMap<Field, String> copy = new EnumMap<>(Field.class);
        copy.putAll(map);
        return Collections.unmodifiableMap(copy);
    }

    /** @return the file's existing effective metadata (v2 first, v1 as fallback); never null */
    public MediaMetadata existing() {
        return file.tags().effective();
    }

    /** @return true if this proposal may be written (NO_PATTERN, MATCHED, AMBIGUOUS_ACCEPTED) */
    public boolean exportable() {
        return status.exportable();
    }
}
