package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.time.Duration;

/**
 * Technical properties of the audio/video stream.
 *
 * @param duration        play time; not null, not negative; zero if unknown
 * @param bitrateKbps     bitrate in kbit/s; ≥ 0, 0 = unknown; the average for VBR
 * @param variableBitrate true for VBR streams
 */
public record StreamInfo(Duration duration, int bitrateKbps, boolean variableBitrate) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if {@code duration} is null
     * @throws IllegalArgumentException if {@code duration} or {@code bitrateKbps} is negative
     */
    public StreamInfo {
        Require.nonNull(duration, "duration");
        Require.that(!duration.isNegative(), "duration must not be negative");
        Require.notNegative(bitrateKbps, "bitrateKbps");
    }
}
