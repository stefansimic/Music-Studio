package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MusicStudioFactoryTest {

    @TempDir
    Path tempDir;

    private MusicStudio studio() {
        return new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"), Clock.systemUTC()))
                .create();
    }

    @Test
    void createGivesWiredUseCasesAndTouchesNothing() {
        assertThat(studio().scan()).isNotNull();
        assertThat(tempDir.resolve("cfg").toFile()).doesNotExist();
        assertThat(tempDir.resolve("data").toFile()).doesNotExist();
    }

    @Test
    void endToEndScanOverRealFiles() {
        Path music = tempDir.resolve("music");
        music.toFile().mkdirs();
        Fixtures.copyTo(Fixture.V23_LATIN1, music);
        Fixtures.copyTo(Fixture.NO_TAGS, music);
        Fixtures.copyTo(Fixture.CORRUPT, music);

        List<ScanItem> items = new ArrayList<>();
        ScanSummary summary = studio().scan().scan(ScanRequest.of(music), items::add, ProgressListener.NONE,
                Cancellation.NONE);

        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(items).extracting(item -> item.path().getFileName().toString())
                .containsExactly("corrupt.mp3", "no-tags.mp3", "v23-latin1.mp3");
        assertThat(items.get(0)).isInstanceOf(UnreadableFile.class);
        assertThat(items.get(1)).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.tags().layers()).isEmpty());
        assertThat(items.get(2)).isInstanceOfSatisfying(ScannedFile.class, f -> {
            assertThat(f.tags().versions()).containsExactly(TagVersion.ID3V2_3);
            assertThat(f.tags().effective().display(Field.TITLE)).contains("Café au lait");
        });
        assertThat(summary.found()).isEqualTo(3);
        assertThat(summary.read()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> new MusicStudioFactory(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MusicStudio(null)).isInstanceOf(NullPointerException.class);
    }
}
