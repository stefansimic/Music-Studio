package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The dry-run result for one file.
 *
 * @param source            absolute original; not null
 * @param target            absolute new file; not null
 * @param format            the format, empty if the file is not a supported format; not null
 * @param action            WRITE or SKIP; WRITE only without errors; not null
 * @param sourceFingerprint fingerprint of the original when planned, empty if it could not be read; not null
 * @param changes           the field changes in the ID3v2 slot, in field order; not null
 * @param request           what the writer gets, present iff action is WRITE; not null
 * @param expectedV2        what the ID3v2 slot must hold after writing (verified by re-reading); not null
 * @param issues            warnings and errors of this item; not null
 */
public record PlannedWrite(Path source, Path target, Optional<MediaFormat> format, PlannedAction action,
                           Optional<FileFingerprint> sourceFingerprint, List<FieldChange> changes,
                           Optional<TagWriteRequest> request, MediaMetadata expectedV2, List<Issue> issues) {

    public PlannedWrite {
        Require.absolute(source, "source");
        Require.absolute(target, "target");
        Require.nonNull(format, "format");
        Require.nonNull(action, "action");
        Require.nonNull(sourceFingerprint, "sourceFingerprint");
        Require.noNullElements(changes, "changes");
        Require.nonNull(request, "request");
        Require.nonNull(expectedV2, "expectedV2");
        Require.noNullElements(issues, "issues");
        changes = List.copyOf(changes);
        issues = List.copyOf(issues);
        boolean hasError = issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
        Require.that(action == PlannedAction.SKIP || !hasError, "an item with errors cannot be written");
        Require.that((action == PlannedAction.WRITE) == request.isPresent(),
                "a WRITE item needs a request, a SKIP item has none");
        Require.that(action != PlannedAction.WRITE || sourceFingerprint.isPresent(),
                "a WRITE item needs the fingerprint of its source");
    }
}
