package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.export.ExportSpec;
import dev.rs.slava.icxc.musicstudio.core.export.ExportStatus;
import dev.rs.slava.icxc.musicstudio.core.export.PlanExportRequest;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Story 003 end to end with the real adapters: scan → propose → plan → apply → re-scan. */
class ExportRealFilesTest {

    @TempDir
    Path tempDir;

    private final Fixture[] fixtures = {Fixture.V24_UTF8, Fixture.V23_UTF16_CYRILLIC, Fixture.V11_V23_DIFF,
        Fixture.NO_TAGS};

    private MusicStudio studio() {
        Path base = tempDir.toAbsolutePath().resolve("app");
        return new MusicStudioFactory(new AppConfig(base.resolve("config"), base.resolve("data"), Clock.systemUTC()))
                .create();
    }

    private List<ScannedFile> scan(MusicStudio studio, Path root) {
        List<ScanItem> items = new ArrayList<>();
        studio.scan().scan(new ScanRequest(root, true, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.LENIENT),
                items::add, ProgressListener.NONE, Cancellation.NONE);
        return items.stream().map(ScannedFile.class::cast).toList();
    }

    private ExportPlan plan(MusicStudio studio, Path src, Path out, List<ScannedFile> files) {
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(),
                Map.of(Field.GENRE, new FieldOverride.Fixed(new FieldValue.Genres(List.of(new Genre("Pop"))))),
                MergeMode.OVERWRITE, Optional.empty(), false);
        List<Proposal> proposals = studio.propose().propose(new ProposeRequest(files, spec, ManualEdits.NONE))
                .proposals();
        return studio.planExport().plan(new PlanExportRequest(proposals, Set.of(src), new ExportSpec(out),
                TagWriteOptions.DEFAULT), ProgressListener.NONE, Cancellation.NONE);
    }

    @Test
    void exportsTaggedCopiesAndLeavesOriginalsAndPayloadUntouched() throws IOException {
        Path src = Files.createDirectories(tempDir.toAbsolutePath().resolve("src"));
        Path out = tempDir.toAbsolutePath().resolve("out");
        for (Fixture fixture : fixtures) {
            Fixtures.copyTo(fixture, src);
        }
        Map<Path, String> hashes = new java.util.TreeMap<>();
        try (Stream<Path> list = Files.list(src)) {
            for (Path file : list.toList()) {
                hashes.put(file, Fixtures.sha256(file) + Files.getLastModifiedTime(file));
            }
        }
        MusicStudio studio = studio();
        ExportPlan plan = plan(studio, src, out, scan(studio, src));
        assertThat(plan.executable()).isTrue();
        assertThat(plan.writeCount()).isEqualTo(fixtures.length);

        ExportReport report = studio.applyExport().apply(plan, ProgressListener.NONE, Cancellation.NONE);

        assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED);
        assertThat(report.written()).hasSize(fixtures.length);
        for (ScannedFile exported : scan(studio, out)) {
            assertThat(exported.tags().versions()).contains(TagVersion.ID3V2_3);
            assertThat(exported.tags().layer(TagFamily.ID3V1)).isPresent();
            assertThat(exported.tags().effective().display(Field.GENRE)).contains("Pop");
            Path original = src.resolve(exported.path().getFileName());
            assertThat(Id3Inspector.inspect(exported.path()).payload())
                    .isEqualTo(Id3Inspector.inspect(original).payload());
        }
        hashes.forEach((file, expected) -> {
            try {
                assertThat(Fixtures.sha256(file) + Files.getLastModifiedTime(file)).isEqualTo(expected);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        try (Stream<Path> list = Files.list(out)) {
            assertThat(list.map(p -> p.getFileName().toString())).noneMatch(n -> n.contains(".musicstudio-tmp"));
        }
    }

    @Test
    void secondExportNeverOverwritesExistingTargets() throws IOException {
        Path src = Files.createDirectories(tempDir.toAbsolutePath().resolve("src"));
        Path out = tempDir.toAbsolutePath().resolve("out");
        Fixtures.copyTo(Fixture.V24_UTF8, src);
        MusicStudio studio = studio();
        List<ScannedFile> files = scan(studio, src);
        studio.applyExport().apply(plan(studio, src, out, files), ProgressListener.NONE, Cancellation.NONE);
        Path written;
        try (Stream<Path> list = Files.list(out)) {
            written = list.findFirst().orElseThrow();
        }
        String before = Fixtures.sha256(written);

        ExportPlan second = plan(studio, src, out, files);

        assertThat(second.executable()).isFalse();
        assertThat(second.files().getFirst().issues()).extracting(i -> i.code()).contains(IssueCode.TARGET_EXISTS);
        assertThat(Fixtures.sha256(written)).isEqualTo(before);
    }
}
