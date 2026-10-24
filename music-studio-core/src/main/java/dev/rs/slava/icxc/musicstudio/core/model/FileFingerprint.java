package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.time.Instant;

/**
 * Size and modification time of a file. Used to detect "source changed since the plan" and "exported file changed
 * since export". Compare fingerprints only when they come from the same source.
 *
 * @param sizeBytes    the file size; ≥ 0
 * @param lastModified the last-modified time; not null
 */
public record FileFingerprint(long sizeBytes, Instant lastModified) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if {@code sizeBytes} is negative
     * @throws NullPointerException     if {@code lastModified} is null
     */
    public FileFingerprint {
        Require.notNegative(sizeBytes, "sizeBytes");
        Require.nonNull(lastModified, "lastModified");
    }
}
