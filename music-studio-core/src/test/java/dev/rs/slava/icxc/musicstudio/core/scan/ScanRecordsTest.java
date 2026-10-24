package dev.rs.slava.icxc.musicstudio.core.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScanRecordsTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    @Test
    void scanRequestValidation() {
        assertThat(ScanRequest.of(ROOT, true).formats()).containsExactly(MediaFormat.MP3);
        assertThatThrownBy(() -> ScanRequest.of(Path.of("rel"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanRequest(ROOT, true, false, Set.of(), Set.of(), V1Comparison.LENIENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanRequest(ROOT, true, false, Set.of(MediaFormat.MP3), Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void scannedFileConflictsMustBePresentInBothLayers() {
        assertThatThrownBy(() -> new ScannedFile(ROOT.resolve("a.mp3"), MediaFormat.MP3,
                new FileFingerprint(1, Instant.EPOCH), TagReadResult.untagged(MediaFormat.MP3), Set.of(Field.TITLE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summaryCountersMustAddUp() {
        assertThatThrownBy(() -> new ScanSummary(ROOT, ScanStatus.COMPLETED, 2, 1, 0, 0, 0, Map.of(), List.of(),
                List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanSummary(ROOT, ScanStatus.COMPLETED, 0, 0, 0, 0, 2, Map.of("jpg", 1),
                List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ScanSummary(ROOT, ScanStatus.CANCELLED, 2, 1, 0, 0, 0, Map.of(), List.of(), List.of()).read())
                .isEqualTo(1);
    }
}
