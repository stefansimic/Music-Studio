package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteExpectation;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteVerifier;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Executable form of the {@link TagWriter} Javadoc contract, checked semantically through a {@link TagReader}:
 * what the copy reads back must equal {@code WriteExpectation.layersAfter} ({@link WriteVerifier}). Every writer
 * (adapters and the fake) extends it. Byte-level guarantees of MP3 writers are in {@code Id3WriterContract}.
 */
public abstract class TagWriterContract {

    private static final FileTime OLD_MTIME = FileTime.from(Instant.parse("2001-02-03T04:05:06Z"));
    /** The new title: Cyrillic, en dash and emoji, so encodings and transliteration are exercised. */
    protected static final String NEW_TITLE = "Ђорђе – Song 🎵";

    @TempDir
    protected Path dir;

    /** @return the writer under test */
    protected abstract TagWriter subject();

    /** @return the reader used to read the copies back */
    protected abstract TagReader reader();

    // --- the matrix --------------------------------------------------------------------------------------------

    static Stream<Arguments> matrix() {
        List<Arguments> arguments = new ArrayList<>();
        for (Fixture fixture : Fixture.values()) {
            if (!fixture.readable()) {
                continue;
            }
            for (Id3v2Choice v2 : Id3v2Choice.values()) {
                for (Id3v1Choice v1 : Id3v1Choice.values()) {
                    for (boolean remove : new boolean[] {false, true}) {
                        if (TagWriteOptions.problems(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, remove).isEmpty()) {
                            arguments.add(Arguments.of(fixture, new TagWriteOptions(v2, v1, V2Encoding.AUTO,
                                    V1TextMode.ASCII, remove)));
                        }
                    }
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrix")
    void everyFixtureAndOptionCombinationReadsBackAsExpected(Fixture fixture, TagWriteOptions options)
            throws Exception {
        FormatCapabilities caps = subject().capabilities(MediaFormat.MP3);
        assumeTrue(caps.unsupported(options).isEmpty(), "writer does not support " + options);
        Path source = copy(fixture);
        TagReadResult before = reader().read(source);
        TagWriteRequest request = request(before, options, changes());
        Path target = dir.resolve("out").resolve(fixture.fileName());
        Files.createDirectories(target.getParent());

        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);

        TagReadResult after = reader().read(target);
        List<TagLayer> expected = WriteExpectation.layersAfter(before.layers(), request);
        assertThat(WriteVerifier.verify(expected, before.stream(), after)).as("%s %s", fixture, options).isEmpty();
        assertUnchanged(fixture, source);
    }

    /** Title, artist set; comment cleared. */
    protected static MediaMetadata changes(MediaMetadata v2) {
        return v2.with(Field.TITLE, new FieldValue.Text(NEW_TITLE))
                .with(Field.ARTIST, new FieldValue.Text("New Artist")).without(Field.COMMENT);
    }

    private static java.util.function.UnaryOperator<MediaMetadata> changes() {
        return TagWriterContract::changes;
    }

    /** Builds a request like the planner does: changes against the v2 slot, v1 projected from the result. */
    protected static TagWriteRequest request(TagReadResult before, TagWriteOptions options,
                                             java.util.function.UnaryOperator<MediaMetadata> change) {
        MediaMetadata v2 = before.layer(TagFamily.ID3V2).map(TagLayer::metadata).orElse(MediaMetadata.empty());
        MediaMetadata proposed = change.apply(before.effective());
        FieldChanges changes = options.id3v2() == Id3v2Choice.NONE ? FieldChanges.NONE
                : FieldChanges.between(v2, change.apply(v2), Set.of(Field.values()));
        Optional<Id3v1Values> v1 = options.id3v1() == Id3v1Choice.NONE ? Optional.empty()
                : Optional.of(Id3v1Projection.project(proposed, options.id3v1(), options.v1TextMode()).values());
        return new TagWriteRequest(MediaFormat.MP3, changes, new CoverChange.Keep(), options, v1);
    }

    // --- hand-written expectations (no core oracle) ------------------------------------------------------------

    @Test
    void v23ToV22PlusV11() throws Exception {
        assumeTrue(subject().capabilities(MediaFormat.MP3).writableVersions().contains(TagVersion.ID3V2_2));
        Path source = copy(Fixture.V23_LATIN1);
        TagWriteOptions options = new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false);
        Path target = write(source, options, m -> m.with(Field.TITLE, new FieldValue.Text("Song")));
        TagReadResult after = reader().read(target);
        assertThat(after.versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_2, TagVersion.ID3V1_1);
        assertThat(after.layer(TagFamily.ID3V2).orElseThrow().metadata().display(Field.TITLE)).contains("Song");
        assertThat(after.layer(TagFamily.ID3V2).orElseThrow().metadata().display(Field.ARTIST)).contains("Artist");
        assertThat(after.layer(TagFamily.ID3V1).orElseThrow().metadata().display(Field.TITLE)).contains("Song");
        assertThat(after.layer(TagFamily.ID3V1).orElseThrow().metadata().display(Field.TRACK)).contains("3");
    }

    @Test
    void v24ToV23WithoutV1() throws Exception {
        Path source = copy(Fixture.V24_UTF8);
        Path target = write(source, new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, false), m -> m);
        TagReadResult after = reader().read(target);
        assertThat(after.versions()).containsExactly(TagVersion.ID3V2_3);
        MediaMetadata v2 = after.layer(TagFamily.ID3V2).orElseThrow().metadata();
        assertThat(v2.display(Field.TITLE)).contains("Ünïcödé Title");
        assertThat(v2.display(Field.YEAR)).contains("2020");
        assertThat(v2.display(Field.GENRE)).contains("Pop; Rock");
    }

    @Test
    void stripV2AndWriteV10() throws Exception {
        Path source = copy(Fixture.V11_V23_DIFF);
        Path target = write(source, new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_0, V2Encoding.AUTO,
                V1TextMode.ASCII, true), m -> m);
        TagReadResult after = reader().read(target);
        assertThat(after.versions()).containsExactly(TagVersion.ID3V1_0);
        assertThat(after.layer(TagFamily.ID3V1).orElseThrow().metadata().display(Field.ARTIST)).contains("Artist A");
    }

    // --- side effects, progress, cancellation ------------------------------------------------------------------

    @Test
    void preflightAndWriteChangeNothingButTheTarget() throws Exception {
        Path source = copy(Fixture.V23_COVER);
        TagWriteRequest request = request(reader().read(source), TagWriteOptions.DEFAULT, changes());
        List<Path> before = listing();
        subject().preflight(source, request);
        assertThat(listing()).isEqualTo(before);
        Path target = dir.resolve("copy.mp3");
        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        List<Path> after = listing();
        assertThat(after).hasSize(before.size() + 1).contains(target);
        assertUnchanged(Fixture.V23_COVER, source);
    }

    @Test
    void progressEndsWithTotal() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        List<long[]> events = new ArrayList<>();
        subject().writeCopy(source, dir.resolve("p.mp3"), request(reader().read(source), TagWriteOptions.DEFAULT,
                changes()), Cancellation.NONE, (done, total) -> events.add(new long[] {done, total}));
        assertThat(events).isNotEmpty();
        long[] last = events.getLast();
        assertThat(last[0]).isEqualTo(last[1]).isPositive();
    }

    @Test
    void cancelledBeforeStart() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        TagWriteRequest request = request(reader().read(source), TagWriteOptions.DEFAULT, changes());
        assertThatThrownBy(() -> subject().writeCopy(source, dir.resolve("c.mp3"), request, cancel,
                ByteProgress.NONE)).isInstanceOf(OperationCancelledException.class);
        assertUnchanged(Fixture.V23_LATIN1, source);
    }

    @Test
    void preconditions() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        TagWriteRequest request = request(reader().read(source), TagWriteOptions.DEFAULT, changes());
        TagWriter writer = subject();
        assertThatThrownBy(() -> writer.writeCopy(null, dir.resolve("x.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> writer.writeCopy(source, null, request, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> writer.writeCopy(source, Path.of("rel.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.writeCopy(source, source, request, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        Path existing = Files.writeString(dir.resolve("exists.mp3"), "x");
        assertThatThrownBy(() -> writer.writeCopy(source, existing, request, Cancellation.NONE, ByteProgress.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(existing)).isEqualTo("x");
        assertThatThrownBy(() -> writer.writeCopy(source, dir.resolve("missing/x.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.preflight(source, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> writer.preflight(Path.of("rel.mp3"), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(Fixture.V23_LATIN1, source);
    }

    @Test
    void unsupportedVersionIsRejectedBeforeWriting() throws Exception {
        FormatCapabilities caps = subject().capabilities(MediaFormat.MP3);
        assumeFalse(caps.writableVersions().contains(TagVersion.ID3V2_2), "writer supports ID3v2.2");
        Path source = copy(Fixture.V23_LATIN1);
        TagWriteRequest request = request(reader().read(source), new TagWriteOptions(Id3v2Choice.V2_2,
                Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII, false), changes());
        assertThatThrownBy(() -> subject().writeCopy(source, dir.resolve("v22.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("v22.mp3")).doesNotExist();
    }

    @Test
    void capabilitiesAreValidAndConstant() {
        assertThat(subject().writableFormats()).contains(MediaFormat.MP3);
        assertThatThrownBy(() -> subject().writableFormats().add(MediaFormat.MP3))
                .isInstanceOf(UnsupportedOperationException.class);
        FormatCapabilities caps = subject().capabilities(MediaFormat.MP3);
        assertThat(caps).isEqualTo(subject().capabilities(MediaFormat.MP3));
        assertThat(caps.writableEncodings()).contains(V2Encoding.AUTO);
        assertThatThrownBy(() -> subject().capabilities(null)).isInstanceOf(NullPointerException.class);
    }

    // --- failures ----------------------------------------------------------------------------------------------

    @Test
    void missingSourceIsNotFound() throws Exception {
        Path source = copy(Fixture.V23_LATIN1);
        TagWriteRequest request = request(reader().read(source), TagWriteOptions.DEFAULT, changes());
        Path missing = dir.resolve("missing.mp3");
        assertThatThrownBy(() -> subject().writeCopy(missing, dir.resolve("t.mp3"), request, Cancellation.NONE,
                ByteProgress.NONE)).isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
    }

    @Test
    void corruptSourceIsCorrupt() throws Exception {
        Path good = copy(Fixture.V23_LATIN1);
        TagWriteRequest request = request(reader().read(good), TagWriteOptions.DEFAULT, changes());
        for (Fixture fixture : List.of(Fixture.CORRUPT, Fixture.EMPTY)) {
            Path source = copy(fixture);
            assertThatThrownBy(() -> subject().writeCopy(source, dir.resolve("t-" + fixture.fileName()), request,
                    Cancellation.NONE, ByteProgress.NONE)).isInstanceOfSatisfying(MediaAccessException.class,
                            e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT));
            assertUnchanged(fixture, source);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readOnlyTargetDirectoryIsAccessDenied() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can write anywhere");
        Path source = copy(Fixture.V23_LATIN1);
        TagWriteRequest request = request(reader().read(source), TagWriteOptions.DEFAULT, changes());
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatThrownBy(() -> subject().writeCopy(source, locked.resolve("t.mp3"), request,
                    Cancellation.NONE, ByteProgress.NONE)).isInstanceOfSatisfying(MediaAccessException.class,
                            e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ACCESS_DENIED));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
        assertUnchanged(Fixture.V23_LATIN1, source);
    }

    @Test
    void concurrentWritesOfDifferentFiles() throws Exception {
        List<Fixture> fixtures = List.of(Fixture.V23_LATIN1, Fixture.V24_UTF8, Fixture.V11_V23_SAME, Fixture.V22);
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<List<?>>> results = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                Path source = copy(fixture);
                TagReadResult before = reader().read(source);
                TagWriteRequest request = request(before, TagWriteOptions.DEFAULT, changes());
                Path target = dir.resolve("concurrent-" + fixture.fileName());
                results.add(pool.submit(() -> {
                    subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
                    return WriteVerifier.verify(WriteExpectation.layersAfter(before.layers(), request),
                            before.stream(), reader().read(target));
                }));
            }
            for (Future<List<?>> result : results) {
                assertThat(result.get()).isEmpty();
            }
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    protected Path copy(Fixture fixture) throws IOException {
        Path file = Fixtures.copyTo(fixture, dir.resolve("src"));
        Files.setLastModifiedTime(file, OLD_MTIME);
        return file;
    }

    protected Path write(Path source, TagWriteOptions options, java.util.function.UnaryOperator<MediaMetadata> change)
            throws Exception {
        Path target = dir.resolve("out").resolve(source.getFileName());
        Files.createDirectories(target.getParent());
        subject().writeCopy(source, target, request(reader().read(source), options, change), Cancellation.NONE,
                ByteProgress.NONE);
        return target;
    }

    protected static void assertUnchanged(Fixture fixture, Path source) throws IOException {
        assertThat(Fixtures.sha256(source)).as("source content of %s", fixture)
                .isEqualTo(Fixtures.sha256(Fixtures.bytes(fixture)));
        assertThat(Files.getLastModifiedTime(source)).as("source mtime of %s", fixture).isEqualTo(OLD_MTIME);
    }

    private List<Path> listing() throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.sorted().toList();
        }
    }
}
