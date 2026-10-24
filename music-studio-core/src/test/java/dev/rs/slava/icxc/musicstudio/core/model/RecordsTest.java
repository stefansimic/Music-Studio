package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Compact-constructor rules of the small model records. */
class RecordsTest {

    @Test
    void streamInfo() {
        assertThat(new StreamInfo(Duration.ZERO, 0, false).bitrateKbps()).isZero();
        assertThatThrownBy(() -> new StreamInfo(null, 1, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StreamInfo(Duration.ofSeconds(-1), 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StreamInfo(Duration.ZERO, -1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coverInfo() {
        assertThat(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 0).sizeBytes()).isZero();
        assertThatThrownBy(() -> new CoverInfo(null, "image/jpeg", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverInfo(PictureType.OTHER, " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverInfo(PictureType.OTHER, "image/png", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coverArtCopiesBytesAndComparesByContent() {
        byte[] data = {1, 2, 3};
        CoverArt art = new CoverArt(data, "image/jpeg", PictureType.FRONT_COVER);
        data[0] = 9;
        assertThat(art.data()).containsExactly(1, 2, 3);
        art.data()[1] = 9;
        assertThat(art.data()).containsExactly(1, 2, 3);
        assertThat(art).isEqualTo(new CoverArt(new byte[] {1, 2, 3}, "image/jpeg", PictureType.FRONT_COVER))
                .hasSameHashCodeAs(new CoverArt(new byte[] {1, 2, 3}, "image/jpeg", PictureType.FRONT_COVER));
        assertThat(art.sizeBytes()).isEqualTo(3);
        assertThatThrownBy(() -> new CoverArt(new byte[0], "image/jpeg", PictureType.OTHER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fileFingerprint() {
        assertThatThrownBy(() -> new FileFingerprint(-1, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileFingerprint(0, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void tagLayer() {
        assertThatThrownBy(() -> new TagLayer(null, MediaMetadata.empty())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TagLayer(TagVersion.ID3V2_3, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void tagVersionLabels() {
        assertThat(TagVersion.ID3V1_1.label()).isEqualTo("ID3v1.1");
        assertThat(TagVersion.ID3V2_2.family()).isEqualTo(TagFamily.ID3V2);
    }
}
