package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Compact-constructor rules of the small model records. */
class RecordsTest {

    @Test
    void streamInfo() {
        assertThat(new StreamInfo(Duration.ofSeconds(1), 128, false).bitrateKbps()).isEqualTo(128);
        assertThatThrownBy(() -> new StreamInfo(Duration.ofSeconds(-1), 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StreamInfo(Duration.ZERO, -1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StreamInfo(null, 0, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void coverInfo() {
        assertThat(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 0).sizeBytes()).isZero();
        assertThatThrownBy(() -> new CoverInfo(PictureType.OTHER, " ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverInfo(PictureType.OTHER, "image/png", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverInfo(null, "image/png", 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fileFingerprint() {
        assertThatThrownBy(() -> new FileFingerprint(-1, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileFingerprint(1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void issue() {
        Issue issue = Issue.warning(IssueCode.READ_VALUE_INVALID, "bad");
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.file()).isEmpty();
        Issue located = issue.forFile(Path.of("/a.mp3").toAbsolutePath()).forField(Field.TRACK);
        assertThat(located.file()).isPresent();
        assertThat(located.field()).contains(Field.TRACK);
        assertThatThrownBy(() -> Issue.error(IssueCode.READ_FAILED, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue.forFile(Path.of("rel.mp3"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Issue(Severity.INFO, IssueCode.READ_FAILED, "m", Optional.of(Path.of("rel")),
                Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThat(Issue.info(IssueCode.V1_V2_CONFLICT, "i").severity()).isEqualTo(Severity.INFO);
        assertThat(Issue.error(IssueCode.READ_FAILED, "e").severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void tagLayerAndVersion() {
        assertThatThrownBy(() -> new TagLayer(null, MediaMetadata.empty())).isInstanceOf(NullPointerException.class);
        assertThat(TagVersion.ID3V2_3.family()).isEqualTo(TagFamily.ID3V2);
        assertThat(TagVersion.ID3V1_1.label()).isEqualTo("ID3v1.1");
    }
}
