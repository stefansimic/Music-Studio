package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Input of {@link ExportFolderUseCase}.
 *
 * @param source        the folder to scan; absolute, not null
 * @param recursive     true = include subfolders
 * @param includeHidden true = include hidden files and folders
 * @param extraction    how to derive new metadata; empty = re-tag the existing metadata only; not null
 * @param outputRoot    the output folder; absolute, not null
 * @param options       the tag write options; not null
 * @param apply         false = dry-run (plan only), true = write when the plan is executable
 */
public record ExportFolderRequest(Path source, boolean recursive, boolean includeHidden,
                                  Optional<ExtractionSpec> extraction, Path outputRoot, TagWriteOptions options,
                                  boolean apply) {

    /** @throws NullPointerException if a component is null; IllegalArgumentException if a path is relative */
    public ExportFolderRequest {
        Require.absolute(source, "source");
        Require.nonNull(extraction, "extraction");
        Require.absolute(outputRoot, "outputRoot");
        Require.nonNull(options, "options");
    }
}
