package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.util.List;

/**
 * What to propose.
 *
 * @param files the scanned files; not null, no nulls (may be empty)
 * @param spec  how to derive the values; not null
 * @param edits manual edits; not null ({@link ManualEdits#NONE})
 */
public record ProposeRequest(List<ScannedFile> files, ExtractionSpec spec, ManualEdits edits) {

    public ProposeRequest {
        Require.noNullElements(files, "files");
        Require.nonNull(spec, "spec");
        Require.nonNull(edits, "edits");
        files = List.copyOf(files);
    }
}
