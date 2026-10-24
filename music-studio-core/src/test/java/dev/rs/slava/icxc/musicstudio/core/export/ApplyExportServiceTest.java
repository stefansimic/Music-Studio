package dev.rs.slava.icxc.musicstudio.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ExportInputs;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeExportTarget.Operation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplyExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private Path src;
    private Path out;
    private FakeTagReader reader;
    private FakeTagWriter writer;
    private FakeExportTarget target;
    private final Map<Path, String> originals = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        src = Files.createDirectories(tmp.resolve("src"));
        out = tmp.resolve("out");
        reader = new FakeTagReader();
        writer = new FakeTagWriter(reader);
        target = new FakeExportTarget();
    }

    /** Originals must be byte- and mtime-identical after every test. */
    @AfterEach
    void originalsAreUntouched() throws Exception {
        for (Map.Entry<Path, String> entry : originals.entrySet()) {
            if (Files.exists(entry.getKey())) {
                assertThat(snapshot(entry.getKey())).isEqualTo(entry.getValue());
            }
        }
    }

    private String snapshot(Path file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return java.util.HexFormat.of().formatHex(hash) + Files.getLastModifiedTime(file).toMillis();
    }

    private Proposal source(String name) throws Exception {
        Path file = src.resolve(name);
        Files.writeString(file, "audio-" + name);
        originals.put(file, snapshot(file));
        ScannedFile scanned = ExportInputs.scanned(reader, file, MediaMetadata.empty(), target.fingerprint(file));
        return ExportInputs.proposal(scanned, Map.of(Field.TITLE, new FieldValue.Text(name)));
    }

    private ExportPlan plan(Proposal... proposals) {
        return new PlanExportService(writer, target, CLOCK).plan(new PlanExportRequest(List.of(proposals),
                Set.of(src), new ExportSpec(out), TagWriteOptions.DEFAULT), ProgressListener.NONE, Cancellation.NONE);
    }

    private ApplyExportService service() {
        return new ApplyExportService(writer, reader, target, CLOCK);
    }

    private ExportReport apply(ExportPlan plan) {
        return service().apply(plan, ProgressListener.NONE, Cancellation.NONE);
    }

    private long tempFiles() throws IOException {
        if (!Files.exists(out)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(out)) {
            return files.filter(p -> p.getFileName().toString().contains(".musicstudio-tmp")).count();
        }
    }

    @Test
    void happyPathWritesVerifiedCopies() throws Exception {
        ExportPlan plan = plan(source("A.mp3"), source("B.mp3"), source("C.mp3"));
        ExportReport report = apply(plan);
        assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED);
        assertThat(report.written()).hasSize(3);
        assertThat(report.written()).allSatisfy(f -> assertThat(f.target()).exists());
        assertThat(report.createdDirectories()).containsExactly(out);
        assertThat(report.written()).extracting(ExportedFile::source).containsExactlyElementsOf(
                plan.files().stream().map(PlannedFile::source).toList());
        for (PlannedFile planned : plan.files()) {
            assertThat(Files.readString(planned.target().orElseThrow()))
                    .isEqualTo(Files.readString(planned.source()));
        }
        assertThat(tempFiles()).isZero();
    }

    @Test
    void verifyMismatchFailsOneFileAndContinues() throws Exception {
        Proposal bad = source("Bad.mp3");
        Proposal good = source("Good.mp3");
        writer.writeWrongValue(bad.file().path());
        ExportReport report = apply(plan(bad, good));
        assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED_WITH_ERRORS);
        assertThat(report.failed()).singleElement().satisfies(f -> {
            assertThat(f.error().code()).isEqualTo(IssueCode.VERIFY_MISMATCH);
            assertThat(f.target().orElseThrow()).doesNotExist();
        });
        assertThat(report.written()).hasSize(1);
        assertThat(tempFiles()).isZero();
    }

    @Test
    void sourceChangedOrDeletedAfterPlanIsNotWritten() throws Exception {
        Proposal changed = source("Changed.mp3");
        Proposal deleted = source("Deleted.mp3");
        ExportPlan plan = plan(changed, deleted);
        originals.keySet().removeAll(List.of(changed.file().path(), deleted.file().path()));
        Files.writeString(changed.file().path(), "different and longer content");
        Files.delete(deleted.file().path());
        ExportReport report = apply(plan);
        assertThat(report.failed()).extracting(f -> f.error().code())
                .containsExactly(IssueCode.SOURCE_CHANGED, IssueCode.SOURCE_CHANGED);
        assertThat(report.written()).isEmpty();
        assertThat(writer.writeCount()).isZero();
    }

    @Test
    void targetAppearingBeforeTheMoveIsNeverReplaced() throws Exception {
        Proposal a = source("A.mp3");
        ExportPlan plan = plan(a);
        Path expected = plan.files().getFirst().target().orElseThrow();
        target.appearBeforeMove(expected);
        ExportReport report = apply(plan);
        assertThat(report.failed()).singleElement().satisfies(
                f -> assertThat(f.error().code()).isEqualTo(IssueCode.TARGET_EXISTS));
        assertThat(Files.readString(expected)).doesNotContain("audio-A.mp3");
        assertThat(tempFiles()).isZero();
    }

    @Test
    void writerFailureFailsOneFileOnly() throws Exception {
        Proposal a = source("A.mp3");
        Proposal b = source("B.mp3");
        writer.failOn(a.file().path(), Reason.ACCESS_DENIED);
        ExportReport report = apply(plan(a, b));
        assertThat(report.status()).isEqualTo(ExportStatus.COMPLETED_WITH_ERRORS);
        assertThat(report.failed()).singleElement().satisfies(
                f -> assertThat(f.error().code()).isEqualTo(IssueCode.WRITE_FAILED));
        assertThat(report.written()).hasSize(1);
    }

    @Test
    void fullDiskAbortsTheRun() throws Exception {
        Proposal p1 = source("1.mp3");
        Proposal p2 = source("2.mp3");
        Proposal p3 = source("3.mp3");
        Proposal p4 = source("4.mp3");
        writer.failOn(p2.file().path(), Reason.NO_SPACE);
        ExportReport report = apply(plan(p1, p2, p3, p4));
        assertThat(report.status()).isEqualTo(ExportStatus.ABORTED);
        assertThat(report.written()).hasSize(1);
        assertThat(report.failed()).hasSize(1);
        assertThat(report.skipped()).hasSize(2);
        assertThat(tempFiles()).isZero();
    }

    @Test
    void cancellationKeepsFinishedFilesAndSkipsTheRest() throws Exception {
        Proposal p1 = source("1.mp3");
        Proposal p2 = source("2.mp3");
        Proposal p3 = source("3.mp3");
        AtomicBoolean cancelled = new AtomicBoolean();
        ProgressListener cancelOnSecond = event -> {
            if (event.current().filter(p2.file().path()::equals).isPresent()) {
                cancelled.set(true);
            }
        };
        ExportReport report = service().apply(plan(p1, p2, p3), cancelOnSecond, cancelled::get);
        assertThat(report.status()).isEqualTo(ExportStatus.CANCELLED);
        assertThat(report.written()).hasSize(1);
        assertThat(report.skipped()).hasSize(2);
        assertThat(tempFiles()).isZero();
    }

    @Test
    void outputFolderCannotBeCreated() throws Exception {
        ExportPlan plan = plan(source("A.mp3"));
        target.failNext(Operation.CREATE_DIRECTORIES, Reason.ACCESS_DENIED);
        ExportReport report = apply(plan);
        assertThat(report.status()).isEqualTo(ExportStatus.ABORTED);
        assertThat(report.written()).isEmpty();
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.issues()).extracting(Issue::code).contains(IssueCode.WRITE_FAILED);
        assertThat(out).doesNotExist();
    }

    @Test
    void planSkipsAppearInTheReportAndCountersAddUp() throws Exception {
        Proposal ok = source("Ok.mp3");
        Proposal none = ExportInputs.unchanged(ok.file(), dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus.NO_MATCH);
        Path otherFile = src.resolve("Other.mp3");
        Files.writeString(otherFile, "x");
        ScannedFile other = ExportInputs.scanned(reader, otherFile, MediaMetadata.empty(), target.fingerprint(otherFile));
        ExportPlan plan = plan(ok, ExportInputs.unchanged(other, dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus.NO_MATCH));
        ExportReport report = apply(plan);
        assertThat(report.written().size() + report.skipped().size() + report.failed().size())
                .isEqualTo(plan.files().size());
        assertThat(report.skipped()).singleElement().satisfies(
                s -> assertThat(s.source()).isEqualTo(otherFile));
        assertThat(none).isNotNull();
    }

    @Test
    void readOnlySourceIsCopiedNormally() throws Exception {
        Proposal a = source("A.mp3");
        assertThat(a.file().path().toFile().setReadOnly()).isTrue();
        assertThat(apply(plan(a)).written()).hasSize(1);
    }

    @Test
    void nonExecutablePlanAndConcurrentApplyAreRejected() throws Exception {
        Proposal a = source("A.mp3");
        ExportPlan empty = plan(ExportInputs.unchanged(a.file(), dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus.NO_MATCH));
        assertThatThrownBy(() -> apply(empty)).isInstanceOf(IllegalArgumentException.class);
        ExportPlan plan = plan(a);
        ApplyExportService service = service();
        List<Throwable> inner = new ArrayList<>();
        service.apply(plan, event -> {
            try {
                service.apply(plan, ProgressListener.NONE, Cancellation.NONE);
            } catch (IllegalStateException e) {
                inner.add(e);
            }
        }, Cancellation.NONE);
        assertThat(inner).isNotEmpty();
    }
}
