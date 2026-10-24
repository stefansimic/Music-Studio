package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * The content of one tag slot of a file.
 *
 * @param version  the exact version found; not null
 * @param metadata the values; not null, may be empty (a tag without values)
 */
public record TagLayer(TagVersion version, MediaMetadata metadata) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is null
     */
    public TagLayer {
        Require.nonNull(version, "version");
        Require.nonNull(metadata, "metadata");
    }
}
