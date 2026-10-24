package dev.rs.slava.icxc.musicstudio.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ExportInputs;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @TempDir
    Path tmp;

    private Path srcRoot;
    private Path out;
    private FakeTagReader reader;
    private FakeExportTarget target;

    @BeforeEach
    void setUp() throws IOException {
        srcRoot = Files.createDirectories(tmp.resolve("src"));
        out = tmp.resolve("out");
        reader = new FakeTagReader();
        target = new FakeExportTarget();
    }

    private PlanExportService service(FakeTagWriter writer) {
        return new PlanExportService(writer, target, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ScannedFile file(String relative, MediaMetadata v2) {
        return ExportInputs.scanned(reader, srcRoot.resolve(relative), v2);
    }

    private static Map<Field, FieldValue> title(String text) {
        return Map.of(Field.TITLE, new FieldValue.Text(text));
    }

    private ExportPlan plan(FakeTagWriter writer, TagWriteOptions options, Path outputRoot, Proposal... proposals) {
        return service(writer).plan(new PlanExportRequest(List.of(proposals), Set.of(srcRoot),
                new ExportSpec(outputRoot), options), ProgressListener.NONE, Cancellation.NONE);
    }

    @Test
    void oneWriteItemPerProposalInOrderWithFlatTarget() throws IOException {
        Files.createDirectories(srcRoot.resolve("a"));
        Proposal a = ExportInputs.proposal(file("a/One.mp3", MediaMetadata.empty()), title("One"));
        Proposal b = ExportInputs.proposal(file("Two.mp3", MediaMetadata.empty()), title("Two"));
        ExportPlan plan = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, a, b);
        assertThat(plan.files()).extracting(PlannedFile::source)
                .containsExactly(a.file().path(), b.file().path());
        assertThat(plan.files()).allSatisfy(f -> {
            assertThat(f.action()).isEqualTo(PlannedAction.WRITE);
            assertThat(f.writeRequest()).isPresent();
        });
        assertThat(plan.files().get(0).target()).contains(out.resolve("One.mp3"));
        assertThat(plan.executable()).isTrue();
        assertThat(plan.writeCount()).isEqualTo(2);
        assertThat(plan.skipCount()).isZero();
        assertThat(plan.createdAt()).isEqualTo(NOW);
    }

    @Test
    void v1ProjectionAppearsInSlotChanges() {
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()),
                Map.of(Field.ARTIST, new FieldValue.Text("Djordje Balasevic")));
        ExportPlan plan = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, p);
        assertThat(plan.files().getFirst().slotChanges()).anySatisfy(change -> {
            assertThat(change.family()).isEqualTo(TagFamily.ID3V1);
            assertThat(change.before()).isEmpty();
            assertThat(change.values()).anySatisfy(v -> assertThat(v.field()).isEqualTo(Field.ARTIST));
        });
    }

    @Test
    void cyrillicArtistInV1ProducesWarnings() {
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()),
                Map.of(Field.ARTIST, new FieldValue.Text("Ђорђе Балашевић")));
        PlannedFile planned = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, p).files().getFirst();
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
        assertThat(planned.issues()).extracting(Issue::severity).contains(Severity.WARNING);
    }

    @Test
    void unsupportedVersionIsAGlobalErrorAndTheWriterIsNeverCalled() {
        FakeTagWriter writer = new FakeTagWriter(reader, EnumSet.of(TagVersion.ID3V1_1, TagVersion.ID3V2_3));
        TagWriteOptions v22 = new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false);
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("S"));
        ExportPlan plan = plan(writer, v22, out, p);
        assertThat(plan.issues()).extracting(Issue::code).contains(IssueCode.UNSUPPORTED_WRITE_VERSION);
        assertThat(plan.executable()).isFalse();
        assertThat(plan.files().getFirst().action()).isEqualTo(PlannedAction.SKIP);
        assertThat(writer.writeCount()).isZero();
        assertThat(target.calls()).isEmpty();
    }

    @Test
    void noMatchAndAmbiguousProposalsAreSkippedWithTheirIssue() {
        Proposal none = ExportInputs.unchanged(file("A.mp3", MediaMetadata.empty()), MatchStatus.NO_MATCH);
        Proposal ambiguous = ExportInputs.unchanged(file("B.mp3", MediaMetadata.empty()), MatchStatus.AMBIGUOUS);
        ExportPlan plan = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, none, ambiguous);
        assertThat(plan.files()).allSatisfy(f -> assertThat(f.action()).isEqualTo(PlannedAction.SKIP));
        assertThat(plan.files().get(0).issues()).extracting(Issue::code).contains(IssueCode.NO_MATCH);
        assertThat(plan.files().get(1).issues()).extracting(Issue::code).contains(IssueCode.AMBIGUOUS_MATCH);
        assertThat(plan.executable()).isFalse();
    }

    @Test
    void sameNameInDifferentFoldersCollidesCaseInsensitively() throws IOException {
        Files.createDirectories(srcRoot.resolve("a"));
        Files.createDirectories(srcRoot.resolve("b"));
        Proposal first = ExportInputs.proposal(file("a/Song.mp3", MediaMetadata.empty()), title("1"));
        Proposal second = ExportInputs.proposal(file("b/song.mp3", MediaMetadata.empty()), title("2"));
        ExportPlan plan = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, first, second);
        assertThat(plan.files().get(0).action()).isEqualTo(PlannedAction.WRITE);
        assertThat(plan.files().get(1).issues()).extracting(Issue::code).contains(IssueCode.COLLISION_SKIPPED);
    }

    @Test
    void existingTargetIsSkipped() throws IOException {
        Files.createDirectories(out);
        Files.writeString(out.resolve("Song.mp3"), "x");
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("S"));
        PlannedFile planned = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, out, p).files().getFirst();
        assertThat(planned.issues()).extracting(Issue::code).contains(IssueCode.TARGET_EXISTS);
    }

    @Test
    void outputRootEqualToSourceFolderIsTargetIsSource() {
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("S"));
        ExportPlan plan = plan(new FakeTagWriter(reader), TagWriteOptions.DEFAULT, srcRoot, p);
        assertThat(plan.files().getFirst().issues()).extracting(Issue::code).contains(IssueCode.TARGET_IS_SOURCE);
        assertThat(plan.issues()).extracting(Issue::code).contains(IssueCode.OUTPUT_INSIDE_SOURCE);
        assertThat(plan.executable()).isFalse();
    }

    @Test
    void latin1EncodingWithCyrillicTitleIsNotEncodable() {
        TagWriteOptions latin1 = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.LATIN1,
                V1TextMode.ASCII, false);
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("Песма"));
        PlannedFile planned = plan(new FakeTagWriter(reader), latin1, out, p).files().getFirst();
        assertThat(planned.action()).isEqualTo(PlannedAction.SKIP);
        assertThat(planned.issues()).extracting(Issue::code).contains(IssueCode.V2_NOT_ENCODABLE);
    }

    @Test
    void preflightFrameLossIsCopiedAndUnreadableFileIsSkipped() {
        ScannedFile lossy = file("Lossy.mp3", MediaMetadata.empty());
        ScannedFile gone = file("Gone.mp3", MediaMetadata.empty());
        FakeTagWriter writer = new FakeTagWriter(reader).frameLoss(lossy.path(),
                List.of(Issue.warning(IssueCode.FRAME_NOT_REPRESENTABLE, "frame X dropped")));
        reader.fail(gone.path(), dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason.NOT_FOUND);
        ExportPlan plan = plan(writer, TagWriteOptions.DEFAULT, out, ExportInputs.proposal(lossy, title("L")),
                ExportInputs.proposal(gone, title("G")));
        assertThat(plan.files().get(0).issues()).extracting(Issue::code).contains(IssueCode.FRAME_NOT_REPRESENTABLE);
        assertThat(plan.files().get(1).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(plan.files().get(1).issues()).extracting(Issue::code).contains(IssueCode.READ_FAILED);
    }

    @Test
    void keepingOtherVersionIsWarnedAndRemovingItIsInfo() {
        MediaMetadata old = MediaMetadata.empty().with(Field.TITLE, new FieldValue.Text("Old"));
        Proposal p = ExportInputs.proposal(file("Song.mp3", old), title("New"));
        TagWriteOptions keep = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false);
        TagWriteOptions remove = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, true);
        FakeTagWriter writer = new FakeTagWriter(reader);
        assertThat(plan(writer, keep, out, p).files().getFirst().issues())
                .extracting(Issue::code).contains(IssueCode.OTHER_VERSION_KEPT);
        assertThat(plan(writer, remove, out, p).files().getFirst().issues())
                .extracting(Issue::code).contains(IssueCode.OTHER_VERSION_REMOVED);
    }

    @Test
    void planningWritesNothing() throws IOException {
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("S"));
        FakeTagWriter writer = new FakeTagWriter(reader);
        plan(writer, TagWriteOptions.DEFAULT, out, p);
        assertThat(writer.writeCount()).isZero();
        assertThat(target.calls()).allSatisfy(call -> assertThat(call).contains("FIND_COLLISION"));
        assertThat(out).doesNotExist();
    }

    @Test
    void cancellationAbortsThePlan() {
        Proposal p = ExportInputs.proposal(file("Song.mp3", MediaMetadata.empty()), title("S"));
        assertThatThrownBy(() -> service(new FakeTagWriter(reader)).plan(new PlanExportRequest(List.of(p),
                Set.of(srcRoot), new ExportSpec(out), TagWriteOptions.DEFAULT), ProgressListener.NONE, () -> true))
                .isInstanceOf(OperationCancelledException.class);
    }

    @Test
    void preconditionsAreChecked() {
        PlanExportService service = service(new FakeTagWriter(reader));
        assertThatThrownBy(() -> service.plan(null, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExportSpec(Path.of("relative"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanExportRequest(List.of(), Set.of(), new ExportSpec(out),
                TagWriteOptions.DEFAULT)).isInstanceOf(IllegalArgumentException.class);
    }
}
