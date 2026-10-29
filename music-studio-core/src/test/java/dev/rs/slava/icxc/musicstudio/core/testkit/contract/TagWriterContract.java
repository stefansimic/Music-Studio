package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.Snapshot;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Contract of {@link TagWriter} for MP3 (spec X01, D02, D08). Every implementation extends this class. All output
 * stays in {@link #tempDir}; fixtures are copied first.
 */
public abstract class TagWriterContract {

    @TempDir
    protected Path tempDir;

    /** @return the implementation under test */
    protected abstract TagWriter subject();

    /** @return a reader that can read what {@link #subject()} writes */
    protected abstract TagReader reader();

    private static final TagWriteOptions DEFAULT = TagWriteOptions.DEFAULT;
    private static final MediaMetadata DESIRED = meta(Field.TITLE, "Contract Title", Field.ARTIST, "Contract Artist");

    /** D08: the output path of a test must be below the JUnit temp directory. */
    protected Path out(String name) {
        Path path = tempDir.resolve(name);
        assertThat(path.startsWith(tempDir)).as("tests only write below @TempDir").isTrue();
        return path;
    }

    private Path copy(Fixture fixture) {
        return Fixtures.copyTo(fixture, tempDir);
    }

    private TagWriteRequest request() {
        return RoundTripSupport.requestFor(DESIRED, DEFAULT);
    }

    // ---- shape (D02)

    @Test
    void writableFormatsAreNonEmptyUnmodifiable() {
        assertThat(subject().writableFormats()).isNotEmpty().contains(MediaFormat.MP3);
        assertThatThrownBy(() -> subject().writableFormats().add(MediaFormat.MP3))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thereIsNoInPlaceWriteMethod() {
        List<String> names = Arrays.stream(TagWriter.class.getMethods()).map(Method::getName).sorted().toList();
        assertThat(names).containsExactlyInAnyOrder("capabilities", "preflight", "writableFormats", "writeCopy");
        Method write = Arrays.stream(TagWriter.class.getMethods()).filter(m -> m.getName().equals("writeCopy"))
                .findFirst().orElseThrow();
        assertThat(Arrays.stream(write.getParameterTypes()).filter(t -> t == Path.class).count()).isEqualTo(2);
    }

    @Test
    void capabilitiesAreConstantAndNonEmpty() {
        var first = subject().capabilities(MediaFormat.MP3);
        assertThat(subject().capabilities(MediaFormat.MP3)).isEqualTo(first);
        assertThat(first.format()).isEqualTo(MediaFormat.MP3);
        assertThat(first.writableVersions()).isNotEmpty();
        assertThat(first.writableFields()).contains(Field.TITLE);
        assertThat(first.writableEncodings()).isNotEmpty();
        assertThatThrownBy(() -> first.writableVersions().add(TagVersion.MP4_ILST))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void capabilitiesRejectNull() {
        assertThatThrownBy(() -> subject().capabilities(null)).isInstanceOf(NullPointerException.class);
    }

    // ---- preconditions

    @Test
    void nullArgumentsAreRejected() {
        Path source = copy(Fixture.NO_TAGS);
        Path target = out("t.mp3");
        TagWriteRequest request = request();
        assertThatThrownBy(() -> subject().writeCopy(null, target, request, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().writeCopy(source, null, request, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().writeCopy(source, target, null, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().writeCopy(source, target, request, null, ByteProgress.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().writeCopy(source, target, request, Cancellation.NONE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().preflight(null, request)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().preflight(source, null)).isInstanceOf(NullPointerException.class);
        assertThat(target).doesNotExist();
    }

    @Test
    void relativePathsAreRejected() {
        Path source = copy(Fixture.NO_TAGS);
        TagWriteRequest request = request();
        assertThatThrownBy(() -> subject().writeCopy(Path.of("a.mp3"), out("t.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject().writeCopy(source, Path.of("t.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject().preflight(Path.of("a.mp3"), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existingTargetIsNeverOverwritten() throws IOException {
        Path source = copy(Fixture.V23_LATIN1);
        Path target = out("existing.mp3");
        Files.writeString(target, "precious");
        Snapshot sourceBefore = RoundTripSupport.snapshot(source);
        Snapshot targetBefore = RoundTripSupport.snapshot(target);
        assertThatThrownBy(() -> subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        RoundTripSupport.assertUnchanged(target, targetBefore);
        RoundTripSupport.assertUnchanged(source, sourceBefore);
    }

    @Test
    void targetEqualToSourceIsRejectedAndSourceUntouched() {
        Path source = copy(Fixture.V23_LATIN1);
        Snapshot before = RoundTripSupport.snapshot(source);
        assertThatThrownBy(() -> subject().writeCopy(source, source, request(), Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        Path viaDots = tempDir.resolve("sub").resolve("..").resolve(source.getFileName());
        assertThatThrownBy(() -> subject().writeCopy(source, viaDots, request(), Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        RoundTripSupport.assertUnchanged(source, before);
    }

    @Test
    void symlinkToSourceAsTargetIsRejectedOrNotCreatedOverTheSource() throws IOException {
        Path source = copy(Fixture.V23_LATIN1);
        Path link = out("link.mp3");
        try {
            Files.createSymbolicLink(link, source);
        } catch (UnsupportedOperationException | IOException e) {
            assumeFalse(true, "symbolic links not available");
        }
        Snapshot before = RoundTripSupport.snapshot(source);
        assertThatThrownBy(() -> subject().writeCopy(source, link, request(), Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        RoundTripSupport.assertUnchanged(source, before);
    }

    @Test
    void missingTargetFolderIsRejected() {
        Path source = copy(Fixture.NO_TAGS);
        Path target = tempDir.resolve("nope").resolve("t.mp3");
        assertThatThrownBy(() -> subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tempDir.resolve("nope")).doesNotExist();
    }

    @Test
    void missingSourceIsNotFound() {
        Path source = tempDir.resolve("missing.mp3");
        Path target = out("t.mp3");
        assertThatThrownBy(() -> subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
        assertThatThrownBy(() -> subject().preflight(source, request())).isInstanceOfSatisfying(
                MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
    }

    @Test
    void sourceOfAnotherFormatIsRejected() throws IOException {
        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "x");
        assertThatThrownBy(() -> subject().writeCopy(source, out("t.mp3"), request(), Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionsTheWriterCannotWriteAreRejected() {
        var caps = subject().capabilities(MediaFormat.MP3);
        TagVersion missing = Arrays.stream(TagVersion.values()).filter(v -> v.family() != TagFamily.MP4)
                .filter(v -> !caps.writableVersions().contains(v)).findFirst().orElse(null);
        assumeFalse(missing == null, "the writer can write every ID3 version");
        Id3v2Choice v2 = Arrays.stream(Id3v2Choice.values()).filter(c -> c.version().equals(java.util.Optional.of(missing)))
                .findFirst().orElse(Id3v2Choice.V2_3);
        TagWriteOptions options = new TagWriteOptions(v2, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false);
        Path source = copy(Fixture.NO_TAGS);
        assertThatThrownBy(() -> subject().writeCopy(source, out("t.mp3"),
                RoundTripSupport.requestFor(DESIRED, options), Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- behaviour

    @Test
    void cancellationBeforeStartThrowsAndLeavesSourceUntouched() {
        Path source = copy(Fixture.V23_LATIN1);
        Snapshot before = RoundTripSupport.snapshot(source);
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        assertThatThrownBy(() -> subject().writeCopy(source, out("t.mp3"), request(), cancel, ByteProgress.NONE))
                .isInstanceOf(OperationCancelledException.class);
        RoundTripSupport.assertUnchanged(source, before);
    }

    @Test
    void writesRequestedFieldsAndKeepsSourceUntouched() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        Snapshot before = RoundTripSupport.snapshot(source);
        Path target = out("t.mp3");
        subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE);
        RoundTripSupport.assertUnchanged(source, before);
        TagReadResult result = reader().read(target);
        assertThat(RoundTripSupport.v2(result).display(Field.TITLE)).contains("Contract Title");
        assertThat(result.versions()).contains(TagVersion.ID3V2_3);
    }

    @Test
    void audioPayloadIsIdentical() {
        Path source = copy(Fixture.V23_LATIN1);
        Path target = out("t.mp3");
        try {
            subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE);
        } catch (MediaAccessException e) {
            throw new AssertionError(e);
        }
        assertThat(Id3Raw.audioPayload(RoundTripSupport.bytes(target)))
                .isEqualTo(Id3Raw.audioPayload(RoundTripSupport.bytes(source)));
    }

    @Test
    void progressEndsWithTotalAndTotal() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        long size = Files.size(source);
        List<long[]> calls = new ArrayList<>();
        AtomicLong last = new AtomicLong(-1);
        subject().writeCopy(source, out("t.mp3"), request(), Cancellation.NONE, (done, total) -> {
            calls.add(new long[] {done, total});
            last.set(done);
        });
        assertThat(calls).isNotEmpty();
        long[] finalCall = calls.get(calls.size() - 1);
        assertThat(finalCall[0]).isEqualTo(finalCall[1]);
        assertThat(finalCall[1]).isEqualTo(size);
    }

    @Test
    void preflightWritesNothing() throws Exception {
        Path source = copy(Fixture.V24_UTF8);
        List<String> before = RoundTripSupport.tree(tempDir);
        subject().preflight(source, request());
        assertThat(RoundTripSupport.tree(tempDir)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = Fixture.class, names = {"NO_TAGS", "V1_1", "V23_LATIN1", "V24_UTF8", "V22", "V23_COVER"})
    void everyFixtureCanBeWrittenAndKeepsItsSource(Fixture fixture) throws Exception {
        Path source = copy(fixture);
        Snapshot before = RoundTripSupport.snapshot(source);
        Path target = out("copy-" + fixture.name() + ".mp3");
        subject().writeCopy(source, target, request(), Cancellation.NONE, ByteProgress.NONE);
        RoundTripSupport.assertUnchanged(source, before);
        assertThat(new TreeSet<>(RoundTripSupport.names(tempDir))).contains(target.getFileName().toString());
    }
}
