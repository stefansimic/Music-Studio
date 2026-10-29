package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.WriteOptionsUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;

/**
 * All use cases of the application, wired. CLI and GUI receive exactly this, so both run the same adapter graph.
 *
 * @param scan         lists a folder and reads the tags of its files (story 001); not null
 * @param proposeMetadata derives the tags a pattern, fixed values and numbering would give (story 002); not null
 * @param writeOptions answers what tag versions and encodings can be written (story 003); not null
 * @param writeTags    plans and writes tagged copies of files (story 003); not null
 */
public record MusicStudio(ScanMediaUseCase scan, ProposeMetadataUseCase proposeMetadata,
                          WriteOptionsUseCase writeOptions, WriteTagsUseCase writeTags) {
    public MusicStudio {
        Require.nonNull(scan, "scan");
        Require.nonNull(proposeMetadata, "proposeMetadata");
        Require.nonNull(writeOptions, "writeOptions");
        Require.nonNull(writeTags, "writeTags");
    }
}
