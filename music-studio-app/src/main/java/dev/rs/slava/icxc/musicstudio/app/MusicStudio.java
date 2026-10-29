package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;

/**
 * All use cases of the application, wired. CLI and GUI receive exactly this, so both run the same adapter graph.
 *
 * @param scan lists a folder and reads the tags of its files (story 001); not null
 */
public record MusicStudio(ScanMediaUseCase scan) {
    public MusicStudio {
        Require.nonNull(scan, "scan");
    }
}
