package dev.rs.slava.icxc.musicstudio.app;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.export.PlannedAction;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedWrite;
import dev.rs.slava.icxc.musicstudio.core.export.WriteItem;
import dev.rs.slava.icxc.musicstudio.core.export.WriteOutcome;
import dev.rs.slava.icxc.musicstudio.core.export.WriteStatus;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsPlan;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsReport;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsRequest;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsUseCase;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.Snapshot;
import dev.rs.slava.icxc.musicstudio.tag.jaudiotagger.JaudiotaggerTagReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Plan and apply of the write use case with the real adapters on copied fixtures (T14, T18, E01-E04, E12-E18). */
class WriteTagsRealFilesTest {

    private static final TagWriteOptions V23 = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE,
            V2Encoding.AUTO, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false);

    @TempDir
    Path tempDir;

    private Path in;
    private Path out;
    private WriteTagsUseCase writeTags;
    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    @BeforeEach
    void setUp() throws IOException {
        in = Files.createDirectories(tempDir.resolve("in"));
        out = Files.createDirectories(tempDir.resolve("out"));
        writeTags = new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"),
                Clock.systemUTC())).create().writeTags();
    }

    private Path source(Fixture fixture, String name) {
        return Fixtures.copyTo(fixture, in, name);
    }

    private static WriteItem item(Path source, Path target, MediaMetadata desired) {
        return new WriteItem(source, target, desired);
    }

    private WriteTagsPlan plan(TagWriteOptions options, WriteItem... items) {
        return writeTags.plan(new WriteTagsRequest(List.of(items), options), Cancellation.NONE);
    }

    private WriteTagsReport apply(WriteTagsPlan plan) {
        return writeTags.apply(plan, ProgressListener.NONE, Cancellation.NONE);
    }

    private TagReadResult read(Path file) throws Exception {
        return reader.read(file);
    }

    private List<String> outNames() {
        return RoundTripSupport.names(out);
    }

    private static List<String> messages(List<Issue> issues) {
        return issues.stream().map(i -> i.code() + ": " + i.message()).toList();
    }

    // ---- T14

    @Test
    void planChangesNothingOnDisk() {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Path b = source(Fixture.V24_UTF8, "b.mp3");
        Path c = source(Fixture.NO_TAGS, "c.mp3");
        List<String> before = RoundTripSupport.tree(tempDir);
        WriteTagsPlan plan = plan(TagWriteOptions.DEFAULT, item(a, out.resolve("a.mp3"), meta(Field.TITLE, "X")),
                item(b, out.resolve("b.mp3"), meta(Field.TITLE, "Y")), item(c, out.resolve("c.mp3"),
                        meta(Field.TITLE, "Z")));
        assertThat(plan.items()).hasSize(3).allSatisfy(p -> assertThat(p.action()).isEqualTo(PlannedAction.WRITE));
        assertThat(RoundTripSupport.tree(tempDir)).isEqualTo(before);
    }

    // ---- T18

    @Test
    void applyWritesVerifiesAndLeavesOnlyTheTarget() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Snapshot snapshot = RoundTripSupport.snapshot(a);
        MediaMetadata desired = meta(Field.TITLE, "New Title", Field.ARTIST, "Artist Latin1", Field.TRACK,
                RoundTripSupport.position(3, 12));
        WriteTagsPlan plan = plan(TagWriteOptions.DEFAULT, item(a, out.resolve("a.mp3"), desired));
        assertThat(plan.executable()).isTrue();
        WriteTagsReport report = apply(plan);
        assertThat(messages(report.outcomes().stream().filter(o -> o instanceof WriteOutcome.Failed)
                .map(o -> ((WriteOutcome.Failed) o).error()).toList())).isEmpty();
        assertThat(report.status()).isEqualTo(WriteStatus.COMPLETED);
        assertThat(report.written()).isEqualTo(1);
        assertThat(outNames()).containsExactly("a.mp3");
        TagReadResult result = read(out.resolve("a.mp3"));
        assertThat(RoundTripSupport.v2(result)).isEqualTo(plan.items().get(0).expectedV2());
        assertThat(result.versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        RoundTripSupport.assertUnchanged(a, snapshot);
    }

    @Test
    void secondRunOnTheSameTargetIsRefusedAndNothingChanges() {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        WriteItem item = item(a, out.resolve("a.mp3"), meta(Field.TITLE, "T"));
        apply(plan(V23, item));
        byte[] first = RoundTripSupport.bytes(out.resolve("a.mp3"));
        WriteTagsPlan again = plan(V23, item);
        assertThat(again.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(again.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.TARGET_EXISTS);
        apply(again);
        assertThat(RoundTripSupport.bytes(out.resolve("a.mp3"))).isEqualTo(first);
    }

    // ---- E18

    @Test
    void versionConversionAloneIsWrittenAndAnUpToDateFileIsSkipped() throws Exception {
        Path a = source(Fixture.V24_UTF8, "a.mp3");
        MediaMetadata current = RoundTripSupport.v2(read(a));
        WriteTagsPlan plan = plan(V23, item(a, out.resolve("a.mp3"), current));
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.WRITE);
        assertThat(plan.items().get(0).changes()).isEmpty();
        assertThat(apply(plan).written()).isEqualTo(1);
        assertThat(read(out.resolve("a.mp3")).versions()).containsExactly(TagVersion.ID3V2_3);

        Path b = source(Fixture.V23_LATIN1, "b.mp3");
        WriteTagsPlan noop = plan(V23, item(b, out.resolve("b.mp3"), RoundTripSupport.v2(read(b))));
        assertThat(noop.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(noop.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.NOTHING_TO_DO);
    }

    // ---- E01

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readOnlySourceCanBeWrittenAndStaysReadOnly() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Files.setPosixFilePermissions(a, PosixFilePermissions.fromString("r--r--r--"));
        Snapshot snapshot = RoundTripSupport.snapshot(a);
        try {
            WriteTagsReport report = apply(plan(V23, item(a, out.resolve("a.mp3"), meta(Field.TITLE, "T"))));
            assertThat(report.written()).isEqualTo(1);
            RoundTripSupport.assertUnchanged(a, snapshot);
        } finally {
            Files.setPosixFilePermissions(a, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    // ---- E02

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void unwritableTargetFolderFailsThatItemWithoutLeftoversAndTheRunContinues() throws Exception {
        Path locked = Files.createDirectories(tempDir.resolve("locked"));
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Path b = source(Fixture.V24_UTF8, "b.mp3");
        WriteTagsPlan plan = plan(V23, item(a, locked.resolve("a.mp3"), meta(Field.TITLE, "X")),
                item(b, out.resolve("b.mp3"), meta(Field.TITLE, "Y")));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(locked), "running as root: permissions are not enforced");
            WriteTagsReport report = apply(plan);
            WriteOutcome.Failed failed = (WriteOutcome.Failed) report.outcomes().get(0);
            assertThat(failed.error().code()).isEqualTo(IssueCode.WRITE_FAILED);
            assertThat(failed.error().message()).contains("ACCESS_DENIED");
            assertThat(report.outcomes().get(1)).isInstanceOf(WriteOutcome.Written.class);
            assertThat(RoundTripSupport.names(locked)).isEmpty();
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    // ---- E03 / E04

    @Test
    void sourceDeletedAfterThePlanIsReportedAndOthersAreWritten() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Path b = source(Fixture.V24_UTF8, "b.mp3");
        WriteTagsPlan plan = plan(V23, item(a, out.resolve("a.mp3"), meta(Field.TITLE, "X")),
                item(b, out.resolve("b.mp3"), meta(Field.TITLE, "Y")));
        Files.delete(a);
        WriteTagsReport report = apply(plan);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code())
                .isEqualTo(IssueCode.SOURCE_CHANGED);
        assertThat(outNames()).containsExactly("b.mp3");
    }

    @Test
    void sourceModifiedAfterThePlanIsNotWritten() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        WriteTagsPlan plan = plan(V23, item(a, out.resolve("a.mp3"), meta(Field.TITLE, "X")));
        Files.setLastModifiedTime(a, FileTime.fromMillis(System.currentTimeMillis() + 3_600_000));
        WriteTagsReport report = apply(plan);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code())
                .isEqualTo(IssueCode.SOURCE_CHANGED);
        assertThat(outNames()).isEmpty();
    }

    // ---- E12

    @Test
    void existingTargetIsNeverOverwritten() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Path existing = out.resolve("a.mp3");
        Files.writeString(existing, "precious");
        Snapshot snapshot = RoundTripSupport.snapshot(existing);
        WriteTagsPlan plan = plan(V23, item(a, existing, meta(Field.TITLE, "X")));
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(plan.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.TARGET_EXISTS);
        apply(plan);
        RoundTripSupport.assertUnchanged(existing, snapshot);
        assertThat(outNames()).containsExactly("a.mp3");
    }

    // ---- E13

    @Test
    void targetThatIsTheSourceDirectlyOrViaSymlinkIsRefused() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        Snapshot snapshot = RoundTripSupport.snapshot(a);
        WriteTagsPlan direct = plan(V23, item(a, a, meta(Field.TITLE, "X")));
        assertThat(direct.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.TARGET_IS_SOURCE);
        Path link = out.resolve("link.mp3");
        try {
            Files.createSymbolicLink(link, a);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not available");
        }
        Path dirLink = tempDir.resolve("dirlink");
        Files.createSymbolicLink(dirLink, in);
        WriteTagsPlan viaLink = plan(V23, item(a, link, meta(Field.TITLE, "X")),
                item(a, dirLink.resolve("a.mp3"), meta(Field.TITLE, "X")));
        assertThat(viaLink.items()).allSatisfy(p -> assertThat(p.action()).isEqualTo(PlannedAction.SKIP));
        assertThat(apply(viaLink).written()).isZero();
        RoundTripSupport.assertUnchanged(a, snapshot);
    }

    // ---- E14

    @Test
    void corruptSourceIsReportedWithoutATargetAndOthersGoOn() {
        Path bad = source(Fixture.CORRUPT, "bad.mp3");
        Path empty = source(Fixture.EMPTY, "empty.mp3");
        Path good = source(Fixture.V23_LATIN1, "good.mp3");
        WriteTagsPlan plan = plan(V23, item(bad, out.resolve("bad.mp3"), meta(Field.TITLE, "X")),
                item(empty, out.resolve("empty.mp3"), meta(Field.TITLE, "X")),
                item(good, out.resolve("good.mp3"), meta(Field.TITLE, "X")));
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(plan.items().get(0).issues().get(0).message()).contains("CORRUPT");
        assertThat(plan.items().get(1).action()).isEqualTo(PlannedAction.SKIP);
        WriteTagsReport report = apply(plan);
        assertThat(report.written()).isEqualTo(1);
        assertThat(outNames()).containsExactly("good.mp3");
    }

    // ---- E15

    @Test
    void cancelAfterTwoOfFiveLeavesTwoCompleteFilesAndNoTempFiles() throws Exception {
        List<WriteItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Path s = source(Fixture.V23_LATIN1, "f" + i + ".mp3");
            items.add(item(s, out.resolve("f" + i + ".mp3"), meta(Field.TITLE, "New " + i)));
        }
        WriteTagsPlan plan = writeTags.plan(new WriteTagsRequest(items, V23), Cancellation.NONE);
        CancellationSource cancel = new CancellationSource();
        WriteTagsReport report = writeTags.apply(plan, e -> {
            if (e.done() == 2) {
                cancel.cancel();
            }
        }, cancel);
        assertThat(report.status()).isEqualTo(WriteStatus.CANCELLED);
        assertThat(report.written()).isEqualTo(2);
        assertThat(outNames()).containsExactly("f0.mp3", "f1.mp3").noneMatch(n -> n.contains(ExportTarget.TEMP_MARKER));
        for (String name : outNames()) {
            assertThat(RoundTripSupport.v2(read(out.resolve(name))).display(Field.TITLE)).isPresent();
        }
    }

    // ---- E16

    @Test
    void unsupportedFileTypeIsAnErrorForThatItem() throws IOException {
        Path flac = in.resolve("song.flac");
        Files.writeString(flac, "not really flac");
        WriteTagsPlan plan = plan(V23, item(flac, out.resolve("song.flac"), meta(Field.TITLE, "X")));
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(plan.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.UNSUPPORTED_FORMAT);
        assertThat(apply(plan).written()).isZero();
        assertThat(outNames()).isEmpty();
    }

    // ---- E17

    @Test
    void nonAsciiTargetNameIsWrittenAndVerified() throws Exception {
        Path probe = out.resolve("Ђорђе – 🎵.mp3");
        try {
            Files.writeString(probe, "x");
            Files.delete(probe);
        } catch (java.nio.file.InvalidPathException | IOException e) {
            assumeTrue(false, "file system or locale cannot hold non-ASCII names");
        }
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        WriteTagsReport report = apply(plan(TagWriteOptions.DEFAULT, item(a, probe,
                meta(Field.TITLE, "Ђорђе", Field.TRACK, RoundTripSupport.position(1, 0)))));
        assertThat(report.written()).describedAs(report.outcomes().toString()).isEqualTo(1);
        assertThat(RoundTripSupport.v2(read(probe)).display(Field.TITLE)).contains("Ђорђе");
        assertThat(outNames()).containsExactly("Ђорђе – 🎵.mp3");
    }

    // ---- E29 / E19 through the use case

    @Test
    void stripAllIsReportedInThePlanAndRemovesBothTags() throws Exception {
        Path a = source(Fixture.V11_V23_SAME, "a.mp3");
        TagWriteOptions strip = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, true);
        WriteTagsPlan plan = plan(strip, item(a, out.resolve("a.mp3"), MediaMetadata.empty()));
        assertThat(plan.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.OTHER_VERSION_REMOVED);
        WriteTagsReport report = apply(plan);
        assertThat(report.written()).as(messages(report.outcomes().stream()
                .filter(o -> o instanceof WriteOutcome.Failed).map(o -> ((WriteOutcome.Failed) o).error()).toList())
                .toString()).isEqualTo(1);
        byte[] bytes = RoundTripSupport.bytes(out.resolve("a.mp3"));
        assertThat(Id3Raw.v2(bytes)).isEmpty();
        assertThat(Id3Raw.v1(bytes)).isEmpty();
        assertThat(Id3Raw.audioPayload(bytes)).isEqualTo(Id3Raw.audioPayload(RoundTripSupport.bytes(a)));
    }

    @Test
    void greekIsWrittenAsV2OnlyUnderTheDefaultPolicyWithAWarning() throws Exception {
        Path a = source(Fixture.V23_LATIN1, "a.mp3");
        MediaMetadata greek = meta(Field.TITLE, "Ελευθερία Αρβανιτάκη", Field.ARTIST, "Μίκης Θεοδωράκης");
        WriteTagsPlan plan = plan(TagWriteOptions.DEFAULT, item(a, out.resolve("a.mp3"), greek));
        assertThat(plan.items().get(0).issues()).extracting(Issue::code).contains(IssueCode.V1_SKIPPED_NON_LATIN);
        assertThat(apply(plan).written()).isEqualTo(1);
        TagReadResult result = read(out.resolve("a.mp3"));
        assertThat(result.versions()).containsExactly(TagVersion.ID3V2_3);
        assertThat(RoundTripSupport.v2(result).display(Field.TITLE)).contains("Ελευθερία Αρβανιτάκη");
    }
}
