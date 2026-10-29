package dev.rs.slava.icxc.musicstudio.core.export;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.IN;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.OUT;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.layer;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.read;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Use-case level tests of plan/apply with fakes: nothing here touches the disk. */
class WriteTagsServiceTest {

    private static final TagWriteOptions V23_ONLY = opts(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO,
            V1NonLatinPolicy.SKIP_V1, false);

    private FakeStudio studio;

    @BeforeEach
    void setUp() {
        studio = new FakeStudio();
    }

    private static TagWriteOptions opts(Id3v2Choice v2, Id3v1Choice v1, V2Encoding enc, V1NonLatinPolicy policy,
                                        boolean remove) {
        return new TagWriteOptions(v2, v1, enc, V1TextMode.ASCII, policy, remove);
    }

    private static WriteTagsRequest request(TagWriteOptions options, WriteItem... items) {
        return new WriteTagsRequest(List.of(items), options);
    }

    private static WriteItem item(Path source, String targetName, MediaMetadata desired) {
        return new WriteItem(source, OUT.resolve(targetName), desired);
    }

    private Path v23Source(String name, String title) {
        return studio.addSource(name, read(layer(TagVersion.ID3V2_3, meta(Field.TITLE, title))));
    }

    private static List<IssueCode> codes(List<Issue> issues) {
        return issues.stream().map(Issue::code).toList();
    }

    private static boolean tempLeft(FakeStudio s) {
        return s.target.files().stream().anyMatch(p -> p.getFileName().toString().contains(ExportTarget.TEMP_MARKER));
    }

    // ---- T13 / T14

    @Test
    void planListsFieldChangesWithOldAndNewValueAndIsDeterministic() {
        Path source = v23Source("a.mp3", "Title A");
        var request = request(V23_ONLY, item(source, "a.mp3", meta(Field.TITLE, "Title B")));
        WriteTagsPlan first = studio.service.plan(request, dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        WriteTagsPlan second = studio.service.plan(request, dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(first).isEqualTo(second);
        PlannedWrite planned = first.items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
        assertThat(planned.changes()).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo(Field.TITLE);
            assertThat(c.oldValue()).contains(new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text("Title A"));
            assertThat(c.newValue()).contains(new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text("Title B"));
        });
        assertThat(planned.expectedV2().display(Field.TITLE)).contains("Title B");
        assertThat(first.executable()).isTrue();
    }

    @Test
    void planNeverWritesAndOnlyAsksTheExportTargetWhetherPathsExist() {
        Path source = v23Source("a.mp3", "A");
        studio.service.plan(request(TagWriteOptions.DEFAULT, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(studio.writer.calls()).isEmpty();
        assertThat(studio.target.files()).isEmpty();
        assertThat(studio.target.events()).isNotEmpty().allMatch(e -> e.startsWith("exists:"));
    }

    @Test
    void cancelledPlanStopsEarly() {
        Path source = v23Source("a.mp3", "A");
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                cancel);
        assertThat(plan.items()).isEmpty();
    }

    // ---- T08 / T09 / T23

    @Test
    void unsupportedVersionIsReportedBeforeAnyWork() {
        studio = new FakeStudio(FakeTagWriter.without(TagVersion.ID3V2_2));
        Path source = v23Source("a.mp3", "A");
        TagWriteOptions v22 = opts(Id3v2Choice.V2_2, Id3v1Choice.V1_1, V2Encoding.AUTO, V1NonLatinPolicy.SKIP_V1,
                false);
        var request = request(v22, item(source, "a.mp3", meta(Field.TITLE, "B")));
        WriteTagsPlan plan = studio.service.plan(request, dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.executable()).isFalse();
        assertThat(plan.items()).isEmpty();
        assertThat(plan.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.UNSUPPORTED_WRITE_VERSION);
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
        });
        assertThat(studio.events).noneMatch(e -> e.startsWith("read:"));
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(report.status()).isEqualTo(WriteStatus.REJECTED);
        assertThat(report.outcomes()).isEmpty();
        assertThat(studio.writer.calls()).isEmpty();
        assertThat(studio.target.files()).isEmpty();
        assertThat(studio.target.events().stream().filter(e -> !e.startsWith("exists:"))).isEmpty();
    }

    @Test
    void validateAndPlanAgreeOnTheCode() {
        studio = new FakeStudio(FakeTagWriter.without(TagVersion.ID3V1_0));
        TagWriteOptions v10 = opts(Id3v2Choice.V2_3, Id3v1Choice.V1_0, V2Encoding.AUTO, V1NonLatinPolicy.SKIP_V1,
                false);
        Path source = v23Source("a.mp3", "A");
        var validate = new WriteOptionsService(studio.writer).validate(v10, Set.of(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3));
        WriteTagsPlan plan = studio.service.plan(request(v10, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(validate).isNotEmpty();
        assertThat(codes(plan.issues())).isEqualTo(codes(validate));
    }

    @Test
    void validateIsEmptyForSupportedOptions() {
        assertThat(new WriteOptionsService(studio.writer).validate(TagWriteOptions.DEFAULT,
                Set.of(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3))).isEmpty();
    }

    // ---- T11 / T17 pass-through of adapter preflight warnings

    @Test
    void adapterPreflightWarningsAppearInThePlanPerFile() {
        studio.writer.preflightReturns(List.of(
                Issue.warning(IssueCode.OTHER_VERSION_KEPT, "kept"),
                Issue.warning(IssueCode.FRAME_NOT_REPRESENTABLE, "PRIV dropped")));
        Path source = v23Source("a.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(codes(plan.items().get(0).issues())).contains(IssueCode.OTHER_VERSION_KEPT,
                IssueCode.FRAME_NOT_REPRESENTABLE);
        assertThat(plan.items().get(0).issues()).allMatch(i -> i.file().isPresent());
        assertThat(plan.executable()).isTrue();
    }

    @Test
    void v1FieldsThatCannotBeStoredAreWarnedInThePlan() {
        Path source = v23Source("a.mp3", "A");
        TagWriteOptions v1Only = opts(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, V1NonLatinPolicy.SKIP_V1,
                true);
        WriteTagsPlan plan = studio.service.plan(request(v1Only, item(source, "a.mp3", meta(Field.TITLE, "B",
                Field.ALBUM_ARTIST, "AA", Field.DISC, dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position(1, 2)))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.items().get(0).issues()).filteredOn(i -> i.code() == IssueCode.V1_FIELD_NOT_REPRESENTABLE)
                .extracting(i -> i.field().orElseThrow()).containsExactlyInAnyOrder(Field.ALBUM_ARTIST, Field.DISC);
    }

    @Test
    void v1TruncationAndTransliterationAreWarnedInThePlan() {
        Path source = v23Source("a.mp3", "A");
        MediaMetadata desired = meta(Field.TITLE, "The quick brown fox jumps over the lazy dog again",
                Field.ARTIST, "Müller");
        WriteTagsPlan plan = studio.service.plan(request(TagWriteOptions.DEFAULT, item(source, "a.mp3", desired)),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        PlannedWrite planned = plan.items().get(0);
        assertThat(planned.issues()).anyMatch(i -> i.code() == IssueCode.V1_TRUNCATED && i.field().get() == Field.TITLE);
        assertThat(planned.issues()).anyMatch(i -> i.code() == IssueCode.V1_TRANSLITERATED
                && i.field().get() == Field.ARTIST);
        assertThat(planned.request().orElseThrow().id3v1().orElseThrow().title().length()).isLessThanOrEqualTo(30);
        assertThat(planned.request().orElseThrow().id3v1().orElseThrow().artist()).isEqualTo("Mueller");
    }

    // ---- E20 / E21 / E24 / E27 service level

    @Test
    void greekWithDefaultPolicyWritesV2OnlyAndWarns() {
        Path source = v23Source("a.mp3", "Old");
        MediaMetadata desired = meta(Field.TITLE, "Ελευθερία Αρβανιτάκη", Field.ARTIST, "Μίκης Θεοδωράκης");
        WriteTagsPlan plan = studio.service.plan(request(TagWriteOptions.DEFAULT, item(source, "a.mp3", desired)),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        PlannedWrite planned = plan.items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
        assertThat(codes(planned.issues())).contains(IssueCode.V1_SKIPPED_NON_LATIN)
                .doesNotContain(IssueCode.V1_TRANSLITERATED);
        assertThat(planned.request().orElseThrow().options().id3v1()).isEqualTo(Id3v1Choice.NONE);
        assertThat(planned.request().orElseThrow().id3v1()).isEmpty();
        assertThat(planned.request().orElseThrow().options().id3v2()).isEqualTo(Id3v2Choice.V2_3);
        assertThat(planned.expectedV2().display(Field.TITLE)).contains("Ελευθερία Αρβανιτάκη");
    }

    @Test
    void mixedScriptsWithCodepagePolicySkipV1ButStillWriteV2() {
        Path source = v23Source("a.mp3", "Old");
        MediaMetadata desired = meta(Field.TITLE, "Ελευθερία", Field.ARTIST, "Ђорђе Балашевић");
        TagWriteOptions codepage = opts(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1NonLatinPolicy.CODEPAGE, false);
        PlannedWrite planned = studio.service.plan(request(codepage, item(source, "a.mp3", desired)),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE).items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
        assertThat(codes(planned.issues())).contains(IssueCode.V1_SKIPPED_NON_LATIN);
        assertThat(planned.request().orElseThrow().id3v1()).isEmpty();
    }

    @Test
    void onlyV1RequestedAndSkippedLeavesNothingToDo() {
        Path source = v23Source("a.mp3", "Old");
        TagWriteOptions v1Only = opts(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, V1NonLatinPolicy.SKIP_V1,
                false);
        PlannedWrite planned = studio.service.plan(request(v1Only, item(source, "a.mp3",
                meta(Field.TITLE, "Ελευθερία"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE)
                .items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.SKIP);
        assertThat(codes(planned.issues())).contains(IssueCode.V1_SKIPPED_NON_LATIN, IssueCode.NOTHING_TO_DO);
    }

    @Test
    void forcedLatin1CannotHoldCyrillicSoTheItemIsSkippedWithAnError() {
        Path source = v23Source("a.mp3", "Old");
        TagWriteOptions latin1 = opts(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.LATIN1,
                V1NonLatinPolicy.SKIP_V1, false);
        WriteTagsPlan plan = studio.service.plan(request(latin1, item(source, "a.mp3",
                meta(Field.TITLE, "Ђорђе"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        PlannedWrite planned = plan.items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.SKIP);
        assertThat(planned.issues()).anyMatch(i -> i.code() == IssueCode.V2_NOT_ENCODABLE
                && i.severity() == Severity.ERROR && i.field().get() == Field.TITLE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(studio.writer.calls()).isEmpty();
        assertThat(studio.target.files()).isEmpty();
    }

    @Test
    void latin1HoldsWesternCharacters() {
        Path source = v23Source("a.mp3", "Old");
        TagWriteOptions latin1 = opts(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.LATIN1,
                V1NonLatinPolicy.SKIP_V1, false);
        PlannedWrite planned = studio.service.plan(request(latin1, item(source, "a.mp3",
                meta(Field.TITLE, "Café Müller ñ"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE)
                .items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
    }

    // ---- E12 / E13 / E14 / E16 / E18

    @Test
    void existingTargetIsAnErrorAndNeverOverwritten() {
        Path source = v23Source("a.mp3", "A");
        studio.target.create(OUT.resolve("a.mp3"));
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        PlannedWrite planned = plan.items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.SKIP);
        assertThat(codes(planned.issues())).containsExactly(IssueCode.TARGET_EXISTS);
        studio.service.apply(plan, ProgressListener.NONE, dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(studio.writer.calls()).isEmpty();
        assertThat(studio.target.files()).containsExactly(OUT.resolve("a.mp3"));
    }

    @Test
    void twoItemsWithTheSameTargetCollide() {
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "same.mp3", meta(Field.TITLE, "X")),
                item(b, "same.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.WRITE);
        assertThat(plan.items().get(1).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(codes(plan.items().get(1).issues())).containsExactly(IssueCode.TARGET_EXISTS);
    }

    @Test
    void targetEqualToSourceIsRejectedAlsoWithDifferentCase() {
        Path source = v23Source("Song.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY,
                new WriteItem(source, source, meta(Field.TITLE, "B")),
                new WriteItem(source, IN.resolve("song.MP3"), meta(Field.TITLE, "B")),
                new WriteItem(source, IN.resolve("x/../Song.mp3"), meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.items()).hasSize(3).allSatisfy(p -> {
            assertThat(p.action()).isEqualTo(PlannedAction.SKIP);
            assertThat(codes(p.issues())).containsExactly(IssueCode.TARGET_IS_SOURCE);
        });
    }

    @Test
    void corruptSourceIsSkippedWithTheReason() {
        Path source = IN.resolve("bad.mp3");
        studio.files.addFile(source);
        studio.fakeReader.failWith(source, Reason.CORRUPT);
        Path good = v23Source("good.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(source, "bad.mp3", meta(Field.TITLE, "B")),
                item(good, "good.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(plan.items().get(0).issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.message()).contains("CORRUPT");
        });
        assertThat(plan.items().get(1).action()).isEqualTo(PlannedAction.WRITE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(report.written()).isEqualTo(1);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("good.mp3"));
    }

    @Test
    void unsupportedFormatIsAnErrorAndNothingIsWritten() {
        Path flac = IN.resolve("song.flac");
        studio.files.addFile(flac);
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(flac, "song.flac", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(plan.items().get(0).action()).isEqualTo(PlannedAction.SKIP);
        assertThat(codes(plan.items().get(0).issues())).containsExactly(IssueCode.UNSUPPORTED_FORMAT);
        assertThat(plan.items().get(0).issues().get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(studio.writer.calls()).isEmpty();
    }

    @Test
    void nothingToDoWhenTheFileAlreadyHasTheTags() {
        Path source = v23Source("a.mp3", "Same");
        PlannedWrite planned = studio.service.plan(request(V23_ONLY, item(source, "a.mp3",
                meta(Field.TITLE, "Same"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE)
                .items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.SKIP);
        assertThat(planned.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.NOTHING_TO_DO);
            assertThat(i.severity()).isEqualTo(Severity.INFO);
        });
    }

    @Test
    void versionConversionAloneIsAWriteWithAnEmptyChangeSet() {
        Path source = studio.addSource("a.mp3", read(layer(TagVersion.ID3V2_4, meta(Field.TITLE, "Same"))));
        PlannedWrite planned = studio.service.plan(request(V23_ONLY, item(source, "a.mp3",
                meta(Field.TITLE, "Same"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE)
                .items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
        assertThat(planned.changes()).isEmpty();
        assertThat(planned.request().orElseThrow().changes().isEmpty()).isTrue();
    }

    @Test
    void changedOptionsOnlyForV1AreAWriteToo() {
        Path source = v23Source("a.mp3", "Same");
        TagWriteOptions withV1 = opts(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1NonLatinPolicy.SKIP_V1, false);
        PlannedWrite planned = studio.service.plan(request(withV1, item(source, "a.mp3", meta(Field.TITLE, "Same"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE).items().get(0);
        assertThat(planned.action()).isEqualTo(PlannedAction.WRITE);
    }

    // ---- T18 / T19 / D03 / D04

    @Test
    void applyWritesVerifiesAndReportsTheTarget() throws Exception {
        Path source = v23Source("a.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(TagWriteOptions.DEFAULT, item(source, "a.mp3",
                meta(Field.TITLE, "B", Field.ARTIST, "Art",
                Field.TRACK, dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position(3, 0)))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        List<Long> progress = new ArrayList<>();
        WriteTagsReport report = studio.service.apply(plan, e -> progress.add(e.done()),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(report.status()).isEqualTo(WriteStatus.COMPLETED);
        assertThat(report.written()).isEqualTo(1);
        assertThat(report.outcomes().get(0)).isInstanceOf(WriteOutcome.Written.class);
        assertThat(progress).containsExactly(1L);
        var reread = studio.fakeReader.read(OUT.resolve("a.mp3"));
        assertThat(reread.layer(TagFamily.ID3V2).orElseThrow().metadata()).isEqualTo(plan.items().get(0).expectedV2());
        assertThat(reread.versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("a.mp3"));
        assertThat(tempLeft(studio)).isFalse();
    }

    @Test
    void verificationMismatchIsAnErrorWithTheFieldAndLeavesNoTargetAndNoTemp() {
        studio.writer.writeWrongTitle();
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X")),
                item(b, "b.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(report.status()).isEqualTo(WriteStatus.COMPLETED);
        assertThat(report.failed()).isEqualTo(2);
        assertThat(report.outcomes()).allSatisfy(o -> {
            var failed = (WriteOutcome.Failed) o;
            assertThat(failed.error().code()).isEqualTo(IssueCode.VERIFY_MISMATCH);
            assertThat(failed.error().message()).containsIgnoringCase("title");
        });
        assertThat(studio.writer.calls()).hasSize(2);
        assertThat(studio.target.files()).isEmpty();
    }

    @Test
    void successfulWriteFollowsReserveWriteReadMoveAndTheTargetAppearsOnlyAfterVerification() {
        Path source = v23Source("a.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(source, "a.mp3", meta(Field.TITLE, "B"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        studio.events.clear();
        studio.writer.onWrite((s, t) -> assertThat(studio.target.files()).doesNotContain(OUT.resolve("a.mp3")));
        studio.service.apply(plan, ProgressListener.NONE, dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        List<String> events = List.copyOf(studio.events);
        int reserve = indexStarting(events, "reserve:");
        int write = indexStarting(events, "write:");
        int readTemp = indexStarting(events, "read:" + OUT + "/.");
        int move = indexStarting(events, "move:");
        assertThat(reserve).isGreaterThanOrEqualTo(0).isLessThan(write);
        assertThat(write).isLessThan(readTemp);
        assertThat(readTemp).isLessThan(move);
        String temp = events.get(reserve).substring("reserve:".length());
        assertThat(Path.of(temp).getParent()).isEqualTo(OUT);
        assertThat(events.get(write)).isEqualTo("write:" + temp);
        assertThat(events.get(move)).isEqualTo("move:" + temp + "->" + OUT.resolve("a.mp3"));
        assertThat(events).anyMatch(e -> e.equals("delete:" + temp));
    }

    private static int indexStarting(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    // ---- E03 / E04 / E05

    @Test
    void sourceModifiedAfterThePlanIsNotWritten() {
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X")),
                item(b, "b.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        studio.touch(a);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code())
                .isEqualTo(IssueCode.SOURCE_CHANGED);
        assertThat(report.outcomes().get(1)).isInstanceOf(WriteOutcome.Written.class);
        assertThat(studio.writer.calls()).extracting(FakeTagWriter.Call::source).containsExactly(b);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("b.mp3"));
    }

    @Test
    void sourceThatDisappearedAfterThePlanIsReportedAndTheRunContinues() {
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X")),
                item(b, "b.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        studio.files.remove(a);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code())
                .isEqualTo(IssueCode.SOURCE_CHANGED);
        assertThat(report.written()).isEqualTo(1);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("b.mp3"));
    }

    @Test
    void diskFullDuringWriteFailsTheItemDeletesTheTempAndContinues() {
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        studio.writer.onWrite((s, t) -> {
            if (s.equals(a)) {
                throw new MediaAccessException(Reason.NO_SPACE, t, "no space left on the device", null);
            }
        });
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X")),
                item(b, "b.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        var failed = (WriteOutcome.Failed) report.outcomes().get(0);
        assertThat(failed.error().code()).isEqualTo(IssueCode.WRITE_FAILED);
        assertThat(failed.error().message()).contains("NO_SPACE");
        assertThat(report.written()).isEqualTo(1);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("b.mp3"));
        assertThat(tempLeft(studio)).isFalse();
    }

    @Test
    void unexpectedRuntimeFailureOfTheWriterIsContainedPerItem() {
        Path a = v23Source("a.mp3", "A");
        studio.writer.onWrite((s, t) -> {
            throw new IllegalStateException("library blew up");
        });
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code()).isEqualTo(IssueCode.WRITE_FAILED);
        assertThat(studio.target.files()).isEmpty();
    }

    @Test
    void raceAtTheTargetBecomesTargetExistsAndNothingIsReplaced() {
        Path a = v23Source("a.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        studio.writer.onWrite((s, t) -> studio.target.create(OUT.resolve("a.mp3"))); // someone else creates it
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        assertThat(((WriteOutcome.Failed) report.outcomes().get(0)).error().code())
                .isEqualTo(IssueCode.TARGET_EXISTS);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("a.mp3"));
        assertThat(tempLeft(studio)).isFalse();
    }

    // ---- E15

    @Test
    void cancelAfterTwoOfFiveKeepsTwoCompleteTargets() {
        List<WriteItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(item(v23Source("f" + i + ".mp3", "A" + i), "f" + i + ".mp3", meta(Field.TITLE, "New" + i)));
        }
        WriteTagsPlan plan = studio.service.plan(new WriteTagsRequest(items, V23_ONLY),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        CancellationSource cancel = new CancellationSource();
        WriteTagsReport report = studio.service.apply(plan, e -> {
            if (e.done() == 2) {
                cancel.cancel();
            }
        }, cancel);
        assertThat(report.status()).isEqualTo(WriteStatus.CANCELLED);
        assertThat(report.written()).isEqualTo(2);
        assertThat(report.outcomes()).hasSize(2);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("f0.mp3"), OUT.resolve("f1.mp3"));
    }

    @Test
    void cancelInTheMiddleOfAWriteDeletesTheTemp() {
        Path a = v23Source("a.mp3", "A");
        Path b = v23Source("b.mp3", "B");
        CancellationSource cancel = new CancellationSource();
        studio.writer.onWrite((s, t) -> {
            if (s.equals(b)) {
                cancel.cancel();
                throw new OperationCancelledException();
            }
        });
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X")),
                item(b, "b.mp3", meta(Field.TITLE, "Y"))), dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE, cancel);
        assertThat(report.status()).isEqualTo(WriteStatus.CANCELLED);
        assertThat(report.written()).isEqualTo(1);
        assertThat(studio.target.files()).containsExactly(OUT.resolve("a.mp3"));
        assertThat(tempLeft(studio)).isFalse();
    }

    @Test
    void cancelledBeforeApplyWritesNothing() {
        Path a = v23Source("a.mp3", "A");
        WriteTagsPlan plan = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE);
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE, cancel);
        assertThat(report.status()).isEqualTo(WriteStatus.CANCELLED);
        assertThat(studio.writer.calls()).isEmpty();
    }

    // ---- X06 preconditions

    @Test
    void preconditionsOfTheUseCase() {
        var none = dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE;
        WriteTagsRequest empty = request(V23_ONLY);
        assertThatThrownBy(() -> studio.service.plan(null, none)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> studio.service.plan(empty, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> studio.service.apply(null, ProgressListener.NONE, none))
                .isInstanceOf(NullPointerException.class);
        WriteTagsPlan plan = studio.service.plan(empty, none);
        assertThatThrownBy(() -> studio.service.apply(plan, null, none)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> studio.service.apply(plan, ProgressListener.NONE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteTagsRequest(null, V23_ONLY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteTagsRequest(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteItem(Path.of("rel.mp3"), OUT.resolve("a.mp3"), MediaMetadata.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteItem(IN.resolve("a.mp3"), Path.of("rel.mp3"), MediaMetadata.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteItem(null, OUT.resolve("a.mp3"), MediaMetadata.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteItem(IN.resolve("a.mp3"), OUT.resolve("a.mp3"), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WriteTagsService(null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void plannedWriteWithErrorsCannotBeAWrite() {
        Path a = v23Source("a.mp3", "A");
        PlannedWrite ok = studio.service.plan(request(V23_ONLY, item(a, "a.mp3", meta(Field.TITLE, "X"))),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE).items().get(0);
        List<Issue> withError = List.of(Issue.error(IssueCode.TARGET_EXISTS, "x"));
        assertThatThrownBy(() -> new PlannedWrite(ok.source(), ok.target(), ok.format(), PlannedAction.WRITE,
                ok.sourceFingerprint(), ok.changes(), ok.request(), ok.expectedV2(), withError))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedWrite(ok.source(), ok.target(), ok.format(), PlannedAction.SKIP,
                ok.sourceFingerprint(), ok.changes(), ok.request(), ok.expectedV2(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionsServicePreconditions() {
        var service = new WriteOptionsService(studio.writer);
        assertThatThrownBy(() -> new WriteOptionsService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.validate(null, Set.of(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.validate(TagWriteOptions.DEFAULT, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.capabilities(null)).isInstanceOf(NullPointerException.class);
        assertThat(service.writableFormats()).containsExactly(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3);
        assertThat(service.capabilities(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3).writableVersions())
                .hasSize(5);
    }
}
