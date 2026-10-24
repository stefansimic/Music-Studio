package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.extract.ChangeOrigin;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builders for export tests: scanned files registered in a {@link FakeTagReader}, and proposals for them. */
public final class ExportInputs {

    private ExportInputs() {
    }

    /** A scanned MP3 with an ID3v2.3 tag, registered with the reader. */
    public static ScannedFile scanned(FakeTagReader reader, Path path, MediaMetadata v2) {
        ScannedFile file = ScannedFiles.of(path, v2);
        reader.answer(path, file.tags());
        return file;
    }

    /** A scanned MP3 with an ID3v2.3 tag and the given fingerprint (use the real one for apply tests). */
    public static ScannedFile scanned(FakeTagReader reader, Path path, MediaMetadata v2, FileFingerprint print) {
        ScannedFile base = ScannedFiles.of(path, v2);
        reader.answer(path, base.tags());
        return new ScannedFile(path, base.format(), print, base.tags(), base.conflicts());
    }

    /** A MATCHED proposal that sets the given values on top of the file's existing metadata. */
    public static Proposal proposal(ScannedFile file, Map<Field, FieldValue> values) {
        MediaMetadata existing = file.tags().effective();
        MediaMetadata proposed = existing;
        for (Map.Entry<Field, FieldValue> entry : values.entrySet()) {
            proposed = proposed.with(entry.getKey(), entry.getValue());
        }
        List<FieldChange> changes = new ArrayList<>();
        for (Field field : existing.differingFields(proposed)) {
            changes.add(new FieldChange(field, existing.get(field), proposed.get(field), ChangeOrigin.PATTERN));
        }
        return new Proposal(file, MatchStatus.MATCHED, Optional.empty(), Map.of(), Map.of(), proposed, changes,
                List.of());
    }

    /** A proposal that changes nothing and has the given status (NO_MATCH needs a detail). */
    public static Proposal unchanged(ScannedFile file, MatchStatus status) {
        return new Proposal(file, status, status == MatchStatus.NO_MATCH ? Optional.of("no match") : Optional.empty(),
                Map.of(), Map.of(), file.tags().effective(), List.of(), List.of());
    }

    /** Re-export for readability. */
    public static TagReadResult untagged() {
        return TagReadResult.untagged(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3);
    }
}
