package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.export.ExportStatus;
import dev.rs.slava.icxc.musicstudio.core.export.ExportSpec;
import dev.rs.slava.icxc.musicstudio.core.export.PlanExportRequest;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Enc;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector.Frame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Story 003: untouched frames survive, v2.4 to v2.2, v1 truncation, vanishing and read-only sources (real files). */
class ExportPreservationRealFilesTest {

    private static final byte[] PRIV_DATA = {1, 2, 3, 4, 5, 0, (byte) 0xFF};
    private static final byte[] UNKNOWN_BODY = {9, 8, 7, 6, 0, 1};

    @TempDir
    Path tempDir;

    private Path src;
    private Path out;

    private MusicStudio studio() {
        Path base = tempDir.toAbsolutePath().resolve("app");
        return new MusicStudioFactory(new AppConfig(base.resolve("config"), base.resolve("data"), Clock.systemUTC()))
                .create();
    }

    private Path write(String name, int major, List<Id3Bytes.Frame> frames) throws IOException {
        src = Files.createDirectories(tempDir.toAbsolutePath().resolve("src"));
        out = tempDir.toAbsolutePath().resolve("out");
        Path file = src.resolve(name);
        Files.write(file, Id3Bytes.concat(Id3Bytes.v2Tag(major, frames, 0), Id3Bytes.audio()));
        return file;
    }

    private ExportPlan plan(MusicStudio studio, TagWriteOptions options, String title) {
        List<ScanItem> items = new ArrayList<>();
        studio.scan().scan(new ScanRequest(src, false, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.LENIENT),
                items::add, ProgressListener.NONE, Cancellation.NONE);
        List<ScannedFile> files = items.stream().map(ScannedFile.class::cast).toList();
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(),
                Map.of(Field.TITLE, new FieldOverride.Fixed(new FieldValue.Text(title))),
                MergeMode.OVERWRITE, Optional.empty(), false);
        List<Proposal> proposals = studio.propose().propose(new ProposeRequest(files, spec, ManualEdits.NONE))
                .proposals();
        return studio.planExport().plan(new PlanExportRequest(proposals, Set.of(src), new ExportSpec(out), options),
                ProgressListener.NONE, Cancellation.NONE);
    }

    private static Frame frame(Id3Inspector.V2Tag tag, String id) {
        return tag.frame(id).orElseThrow(() -> new AssertionError("frame " + id + " missing in " + tag.ids()));
    }

    @Test
    void untouchedFramesSurviveTheTitleChange() throws IOException {
        byte[] jpeg = Id3Bytes.jpeg(300);
        Path file = write("Song.mp3", 3, List.of(
                Id3Bytes.text("TIT2", Enc.LATIN1, "Old"), Id3Bytes.text("TPE1", Enc.LATIN1, "Artist"),
                Id3Bytes.txxx("REPLAYGAIN_TRACK_GAIN", "-6.50 dB"),
                Id3Bytes.ownerData("PRIV", "com.example", PRIV_DATA),
                Id3Bytes.apic("image/jpeg", 3, "front", jpeg),
                Id3Bytes.raw("ZZZZ", UNKNOWN_BODY)));
        String sourceHash = Fixtures.sha256(file);
        MusicStudio studio = studio();

        ExportPlan plan = plan(studio, TagWriteOptions.DEFAULT, "New Title");
        ExportReport report = studio.applyExport().apply(plan, ProgressListener.NONE, Cancellation.NONE);

        assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED);
        Id3Inspector.Id3File result = Id3Inspector.inspect(out.resolve("Song.mp3"));
        Id3Inspector.V2Tag tag = result.v2().orElseThrow();
        assertThat(tag.major()).isEqualTo(3);
        assertThat(Id3Inspector.text(frame(tag, "TIT2"))).isEqualTo("New Title");
        assertThat(Id3Inspector.text(frame(tag, "TPE1"))).isEqualTo("Artist");
        assertThat(new String(frame(tag, "TXXX").body(), StandardCharsets.ISO_8859_1))
                .contains("REPLAYGAIN_TRACK_GAIN").contains("-6.50 dB");
        assertThat(frame(tag, "PRIV").body()).endsWith(PRIV_DATA).contains("com.example".getBytes(StandardCharsets.US_ASCII));
        assertThat(frame(tag, "APIC").body()).endsWith(jpeg);
        assertThat(frame(tag, "ZZZZ").body()).isEqualTo(UNKNOWN_BODY);
        assertThat(result.payload()).isEqualTo(Id3Inspector.inspect(file).payload());
        assertThat(Fixtures.sha256(file)).isEqualTo(sourceHash);
    }

    @Test
    void v24OnlySourceBecomesV22WithoutV24Frames() throws IOException {
        write("Song.mp3", 4, List.of(
                Id3Bytes.text("TIT2", Enc.UTF8, "Љубав"), Id3Bytes.text("TPE1", Enc.UTF8, "Ђорђе"),
                Id3Bytes.apic("image/jpeg", 3, "", Id3Bytes.jpeg(200))));
        MusicStudio studio = studio();
        TagWriteOptions v22 = new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, false);

        ExportPlan plan = plan(studio, v22, "Нова");
        assertThat(plan.executable()).isTrue();
        studio.applyExport().apply(plan, ProgressListener.NONE, Cancellation.NONE);

        Id3Inspector.Id3File result = Id3Inspector.inspect(out.resolve("Song.mp3"));
        Id3Inspector.V2Tag tag = result.v2().orElseThrow();
        assertThat(tag.major()).isEqualTo(2);
        assertThat(tag.ids()).allSatisfy(id -> assertThat(id).hasSize(3));
        assertThat(Id3Inspector.text(frame(tag, "TT2"))).isEqualTo("Нова");
        assertThat(Id3Inspector.text(frame(tag, "TP1"))).isEqualTo("Ђорђе");
        assertThat(Id3Inspector.encoding(frame(tag, "TT2"))).isEqualTo(1);
        assertThat(result.v1Block()).isEmpty();
    }

    @Test
    void titleLongerThan30CharsIsTruncatedInV1WithWarningAndFullInV2() throws IOException {
        write("Song.mp3", 3, List.of(Id3Bytes.text("TPE1", Enc.LATIN1, "A")));
        String title = "This is a very long title that exceeds thirty characters";
        MusicStudio studio = studio();

        ExportPlan plan = plan(studio, TagWriteOptions.DEFAULT, title);
        assertThat(plan.files().getFirst().issues()).extracting(Issue::code).contains(IssueCode.V1_TRUNCATED);
        studio.applyExport().apply(plan, ProgressListener.NONE, Cancellation.NONE);

        Id3Inspector.Id3File result = Id3Inspector.inspect(out.resolve("Song.mp3"));
        assertThat(Id3Inspector.text(frame(result.v2().orElseThrow(), "TIT2"))).isEqualTo(title);
        assertThat(Id3Inspector.v1Fields(result.v1Block().orElseThrow()).title()).isEqualTo(title.substring(0, 30));
    }

    @Test
    void sourceVanishingAfterPlanningWritesNothingAndLeavesNoTempFiles() throws IOException {
        Path file = write("Song.mp3", 3, List.of(Id3Bytes.text("TIT2", Enc.LATIN1, "Old")));
        MusicStudio studio = studio();
        ExportPlan plan = plan(studio, TagWriteOptions.DEFAULT, "New");
        Files.delete(file);

        ExportReport report = studio.applyExport().apply(plan, ProgressListener.NONE, Cancellation.NONE);

        assertThat(report.written()).isEmpty();
        assertThat(report.failed().size() + report.skipped().size()).isEqualTo(1);
        if (Files.exists(out)) {
            try (Stream<Path> list = Files.list(out)) {
                assertThat(list).isEmpty();
            }
        }
    }

    @Test
    void readOnlySourceIsExportedAndStaysReadOnlyAndUnchanged() throws IOException {
        Path file = write("Song.mp3", 3, List.of(Id3Bytes.text("TIT2", Enc.LATIN1, "Old")));
        assertThat(file.toFile().setReadOnly()).isTrue();
        String hash = Fixtures.sha256(file);
        try {
            MusicStudio studio = studio();
            ExportReport report = studio.applyExport().apply(plan(studio, TagWriteOptions.DEFAULT, "New"),
                    ProgressListener.NONE, Cancellation.NONE);

            assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED);
            assertThat(report.written()).hasSize(1);
            assertThat(Files.isWritable(file)).isFalse();
            assertThat(Fixtures.sha256(file)).isEqualTo(hash);
            assertThat(Id3Inspector.text(frame(Id3Inspector.inspect(out.resolve("Song.mp3")).v2().orElseThrow(),
                    "TIT2"))).isEqualTo("New");
        } finally {
            file.toFile().setWritable(true);
        }
    }
}
