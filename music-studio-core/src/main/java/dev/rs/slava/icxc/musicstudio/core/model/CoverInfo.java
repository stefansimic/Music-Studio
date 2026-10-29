package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Describes an embedded picture without holding its bytes.
 *
 * @param type      not null
 * @param mimeType  not blank
 * @param sizeBytes {@code >= 0}
 */
public record CoverInfo(PictureType type, String mimeType, int sizeBytes) {
    public CoverInfo {
        Require.nonNull(type, "type");
        Require.nonBlank(mimeType, "mimeType");
        Require.that(sizeBytes >= 0, "sizeBytes must be >= 0");
    }
}
