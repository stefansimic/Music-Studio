package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The proposed final metadata of one file.
 *
 * @param file        the scanned file; not null
 * @param status      not null
 * @param matchDetail why there is no match, or the chosen split of an ambiguous match; not null
 * @param extracted   the captured name parts (NFC, stripped) of the chosen split; empty without a match; not null
 * @param proposed    the metadata the file should get; equals the current tags for NO_MATCH; not null
 * @param changes     the fields where {@code proposed} differs from the current tags, in field order; not null
 * @param issues      notes and warnings; not null
 */
public record Proposal(ScannedFile file, MatchStatus status, Optional<String> matchDetail,
                       Map<Field, String> extracted, MediaMetadata proposed, List<ProposedChange> changes,
                       List<Issue> issues) {

    public Proposal {
        Require.nonNull(file, "file");
        Require.nonNull(status, "status");
        Require.nonNull(matchDetail, "matchDetail");
        Require.noNullEntries(extracted, "extracted");
        Require.nonNull(proposed, "proposed");
        Require.noNullElements(changes, "changes");
        Require.noNullElements(issues, "issues");
        EnumMap<Field, String> copy = new EnumMap<>(Field.class);
        copy.putAll(extracted);
        extracted = Collections.unmodifiableMap(copy);
        changes = List.copyOf(changes);
        issues = List.copyOf(issues);
    }

    /** @return the tags the file has now (the effective values of its tag layers); never null */
    public MediaMetadata existing() {
        return file.tags().effective();
    }

    /** @return true for NO_PATTERN, MATCHED and AMBIGUOUS_ACCEPTED: the proposal may be written */
    public boolean exportable() {
        return status == MatchStatus.NO_PATTERN || status == MatchStatus.MATCHED
                || status == MatchStatus.AMBIGUOUS_ACCEPTED;
    }
}
