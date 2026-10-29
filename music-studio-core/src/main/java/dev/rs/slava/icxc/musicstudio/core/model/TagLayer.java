package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * The content of one tag slot of a file.
 *
 * @param version  the exact version found; not null
 * @param metadata the decoded values; not null
 */
public record TagLayer(TagVersion version, MediaMetadata metadata) {
    public TagLayer {
        Require.nonNull(version, "version");
        Require.nonNull(metadata, "metadata");
    }
}
