package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.time.Instant;

/**
 * Size and modification time of a file, used to detect "changed since".
 *
 * @param sizeBytes    {@code >= 0}
 * @param lastModified not null
 */
public record FileFingerprint(long sizeBytes, Instant lastModified) {
    public FileFingerprint {
        Require.notNegative(sizeBytes, "sizeBytes");
        Require.nonNull(lastModified, "lastModified");
    }
}
