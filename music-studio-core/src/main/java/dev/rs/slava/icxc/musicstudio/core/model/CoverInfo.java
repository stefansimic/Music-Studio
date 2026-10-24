package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Describes an embedded picture without holding its bytes.
 *
 * @param type      the picture's role; not null
 * @param mimeType  e.g. "image/jpeg"; not blank
 * @param sizeBytes the size of the picture data; ≥ 0
 */
public record CoverInfo(PictureType type, String mimeType, int sizeBytes) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if {@code mimeType} is blank or {@code sizeBytes} is negative
     */
    public CoverInfo {
        Require.nonNull(type, "type");
        Require.nonBlank(mimeType, "mimeType");
        Require.notNegative(sizeBytes, "sizeBytes");
    }
}
