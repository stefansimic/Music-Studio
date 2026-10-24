package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MusicStudioFactoryTest {

    @TempDir
    Path dir;

    private MusicStudio studio() {
        Path base = dir.toAbsolutePath().resolve("app");
        return new MusicStudioFactory(new AppConfig(base.resolve("config"), base.resolve("data"), Clock.systemUTC()))
                .create();
    }

    @Test
    void createWiresAllUseCasesAndCreatesNoDirectories() {
        assertThat(studio().scan()).isNotNull();
        assertThat(studio().propose()).isNotNull();
        assertThat(dir.resolve("app")).doesNotExist();
    }

    @Test
    void endToEndScanAndProposeChangesNoFile() {
        Path album = dir.toAbsolutePath().resolve("Best Of 2020");
        Path song = Fixtures.copyTo(Fixture.V23_LATIN1, album, "Artist - Song - 2020.mp3");
        Path other = Fixtures.copyTo(Fixture.NO_TAGS, album, "Other - Name - 1999.mp3");
        String before = Fixtures.sha256(song) + Fixtures.sha256(other);
        MusicStudio studio = studio();
        List<ScannedFile> files = new ArrayList<>();
        studio.scan().scan(ScanRequest.of(album, false), item -> files.add((ScannedFile) item), ProgressListener.NONE,
                Cancellation.NONE);
        InputPattern pattern = ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(
                "{album}/{artist} - {title} - {year}")).pattern();
        ProposalSet set = studio.propose().propose(new ProposeRequest(files, ExtractionSpec.of(pattern),
                ManualEdits.NONE));
        assertThat(set.matched()).isEqualTo(2);
        assertThat(set.proposals()).filteredOn(p -> p.file().path().equals(song)).singleElement().satisfies(p -> {
            assertThat(p.existing().display(Field.TITLE)).contains("Café Song");
            assertThat(p.proposed().display(Field.TITLE)).contains("Song");
            assertThat(p.proposed().display(Field.ALBUM)).contains("Best Of 2020");
        });
        assertThat(Fixtures.sha256(song) + Fixtures.sha256(other)).isEqualTo(before);
    }

    @Test
    void endToEndScanOfRealFixtures() {
        Path music = dir.toAbsolutePath().resolve("music");
        Fixtures.copyTo(Fixture.V23_LATIN1, music);
        Fixtures.copyTo(Fixture.NO_TAGS, music);
        Fixtures.copyTo(Fixture.CORRUPT, music);

        List<ScanItem> items = new ArrayList<>();
        ScanSummary summary = studio().scan().scan(ScanRequest.of(music, false), items::add, ProgressListener.NONE,
                Cancellation.NONE);

        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(items).extracting(item -> item.path().getFileName().toString())
                .containsExactly("corrupt.mp3", "no-tags.mp3", "v23-latin1.mp3");
        assertThat(items.get(0)).isInstanceOf(UnreadableFile.class);
        assertThat(items.get(1)).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.tags().layers()).isEmpty());
        assertThat(items.get(2)).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.tags().layers()).isEqualTo(FixtureCatalog.require(Fixture.V23_LATIN1).layers()));
    }
}
