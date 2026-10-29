package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.time.Duration;

/**
 * Audio stream properties.
 *
 * @param duration        not null, not negative; zero if unknown
 * @param bitrateKbps     {@code >= 0}; 0 = unknown; the average for variable bitrate
 * @param variableBitrate true for VBR
 */
public record StreamInfo(Duration duration, int bitrateKbps, boolean variableBitrate) {
    public StreamInfo {
        Require.nonNull(duration, "duration");
        Require.that(!duration.isNegative(), "duration must not be negative");
        Require.that(bitrateKbps >= 0, "bitrateKbps must be >= 0");
    }
}
