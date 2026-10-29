package dev.rs.slava.icxc.musicstudio.core.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScanRecordsTest {

    private static final Path FILE = Path.of("/music/a.mp3").toAbsolutePath();
    private static final FileFingerprint FP = new FileFingerprint(1, Instant.EPOCH);

    @Test
    void scannedFileConflictsMustBePresentInBothLayers() {
        var tags = FixtureCatalog.readable(Fixture.V11_V23_DIFF);
        assertThat(new ScannedFile(FILE, MediaFormat.MP3, FP, tags, Set.of(Field.ARTIST)).conflicts())
                .containsExactly(Field.ARTIST);
        assertThatThrownBy(() -> new ScannedFile(FILE, MediaFormat.MP3, FP, tags, Set.of(Field.ALBUM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unreadableFileNeedsAnErrorIssue() {
        assertThatThrownBy(() -> new UnreadableFile(FILE, MediaFormat.MP3, Issue.warning(IssueCode.READ_FAILED, "w")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new UnreadableFile(FILE, MediaFormat.MP3, Issue.error(IssueCode.READ_FAILED, "e")).path())
                .isEqualTo(FILE);
    }

    @Test
    void summaryInvariants() {
        Path root = FILE.getParent();
        assertThatThrownBy(() -> new ScanSummary(root, ScanStatus.COMPLETED, 3, 1, 1, 0, Map.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("completed");
        assertThat(new ScanSummary(root, ScanStatus.CANCELLED, 3, 1, 0, 0, Map.of(), List.of(), List.of()).read())
                .isEqualTo(1);
        assertThatThrownBy(() -> new ScanSummary(root, ScanStatus.CANCELLED, 1, 1, 1, 0, Map.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanSummary(root, ScanStatus.COMPLETED, 0, 0, 0, 2, Map.of("jpg", 1), List.of(),
                List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
