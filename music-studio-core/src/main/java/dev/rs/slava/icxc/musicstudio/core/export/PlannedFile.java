package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.tag.SlotChange;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One file of an {@link ExportPlan}.
 *
 * <p>Invariants: WRITE ⇔ target and writeRequest present and no ERROR issue. SKIP ⇒ at least one ERROR issue and
 * {@code expectedLayers.equals(currentLayers)}.
 *
 * @param source            absolute source file; not null
 * @param sourceFingerprint the source's fingerprint when it was scanned; not null
 * @param format            the file's format; not null
 * @param action            WRITE or SKIP; not null
 * @param target            absolute target path; not null (empty for some skips)
 * @param changes           value changes per field (effective values, before → after); not null
 * @param writeRequest      what the writer must do; not null (present iff WRITE)
 * @param currentLayers     the tag layers in the source; not null
 * @param expectedLayers    the layers a conforming writer leaves in the target; not null
 * @param issues            warnings and errors for this file; not null
 */
public record PlannedFile(Path source, FileFingerprint sourceFingerprint, MediaFormat format, PlannedAction action,
                          Optional<Path> target, List<FieldChange> changes, Optional<TagWriteRequest> writeRequest,
                          List<TagLayer> currentLayers, List<TagLayer> expectedLayers, List<Issue> issues) {

    /** @throws NullPointerException if a component or element is null; IllegalArgumentException if violated */
    public PlannedFile {
        Require.absolute(source, "source");
        Require.nonNull(sourceFingerprint, "sourceFingerprint");
        Require.nonNull(format, "format");
        Require.nonNull(action, "action");
        Require.nonNull(target, "target");
        target.ifPresent(t -> Require.absolute(t, "target"));
        Require.noNullElements(changes, "changes");
        Require.nonNull(writeRequest, "writeRequest");
        Require.noNullElements(currentLayers, "currentLayers");
        Require.noNullElements(expectedLayers, "expectedLayers");
        Require.noNullElements(issues, "issues");
        boolean hasError = issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
        if (action == PlannedAction.WRITE) {
            Require.that(target.isPresent() && writeRequest.isPresent() && !hasError,
                    "WRITE needs a target and a request and no ERROR issue");
        } else {
            Require.that(hasError, "SKIP needs at least one ERROR issue");
            Require.that(expectedLayers.equals(currentLayers), "SKIP leaves the layers unchanged");
        }
        changes = List.copyOf(changes);
        currentLayers = List.copyOf(currentLayers);
        expectedLayers = List.copyOf(expectedLayers);
        issues = List.copyOf(issues);
    }

    /** @return the per-tag-slot differences between the current and the expected layers; never null */
    public List<SlotChange> slotChanges() {
        return SlotChange.between(currentLayers, expectedLayers);
    }
}
