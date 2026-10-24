package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ApplyExportUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.PlanExportUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.WriteOptionsUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;

/**
 * All use cases of the application, wired by {@link MusicStudioFactory}. CLI and GUI receive the same instance
 * shape, so anything one frontend can do the other does through the same use case.
 *
 * @param scan    list a folder and read the tags of its media files; not null
 * @param propose compute proposed metadata from names, fixed values, auto-numbering and edits (pure); not null
 * @param writeOptions what can be written and whether options are possible; not null
 * @param planExport    the export dry-run; not null
 * @param applyExport   the export; not null
 * @param exportFolder  the whole folder flow (scan, propose, plan, apply); not null
 */
public record MusicStudio(ScanMediaUseCase scan, ProposeMetadataUseCase propose, WriteOptionsUseCase writeOptions,
                          PlanExportUseCase planExport, ApplyExportUseCase applyExport,
                          ExportFolderUseCase exportFolder) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is null
     */
    public MusicStudio {
        Require.nonNull(scan, "scan");
        Require.nonNull(propose, "propose");
        Require.nonNull(writeOptions, "writeOptions");
        Require.nonNull(planExport, "planExport");
        Require.nonNull(applyExport, "applyExport");
        Require.nonNull(exportFolder, "exportFolder");
    }
}
