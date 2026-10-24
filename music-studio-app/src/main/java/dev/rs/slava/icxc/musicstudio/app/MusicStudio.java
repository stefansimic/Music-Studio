package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;

/**
 * All use cases of the application, wired by {@link MusicStudioFactory}. CLI and GUI receive the same instance
 * shape, so anything one frontend can do the other does through the same use case.
 *
 * @param scan    list a folder and read the tags of its media files; not null
 * @param propose compute proposed metadata from names, fixed values, auto-numbering and edits (pure); not null
 */
public record MusicStudio(ScanMediaUseCase scan, ProposeMetadataUseCase propose) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is null
     */
    public MusicStudio {
        Require.nonNull(scan, "scan");
        Require.nonNull(propose, "propose");
    }
}
