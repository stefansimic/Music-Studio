package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Input of {@link ProposeMetadataUseCase#propose}.
 *
 * @param files the scanned files, in the order the proposals should have; unique paths; no nulls; copied
 * @param spec  how to compute the proposals; not null
 * @param edits manual edits; not null ({@link ManualEdits#NONE})
 */
public record ProposeRequest(List<ScannedFile> files, ExtractionSpec spec, ManualEdits edits) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or element is null
     * @throws IllegalArgumentException if a path occurs twice
     */
    public ProposeRequest {
        Require.noNullElements(files, "files");
        Require.nonNull(spec, "spec");
        Require.nonNull(edits, "edits");
        Set<Path> seen = new HashSet<>();
        for (ScannedFile file : files) {
            Require.that(seen.add(file.path()), "duplicate file %s", file.path());
        }
        files = List.copyOf(files);
    }
}
