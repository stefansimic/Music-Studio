package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Executable form of the {@link TagReader} Javadoc contract. Every reader implementation (adapters and fakes)
 * extends this class and passes it.
 *
 * <p>Fixtures are always copied into a {@code @TempDir} before use. The contract for {@code readCover} is added
 * with story 009.
 */
public abstract class TagReaderContract {

    /** A fixed, old modification time set on every copy, so a changed mtime is detectable. */
    private static final FileTime OLD_MTIME = FileTime.from(Instant.parse("2001-02-03T04:05:06Z"));

    @TempDir
    protected Path dir;

    /** @return the reader under test; may be a new instance per call */
    protected abstract TagReader subject();

    // ---------------------------------------------------------------------------------------------------------

    @Test
    void readableFormatsAreNonEmptyAndUnmodifiable() {
        var formats = subject().readableFormats();
        assertThat(formats).isNotEmpty().contains(MediaFormat.MP3);
        assertThatThrownBy(() -> formats.add(MediaFormat.MP3)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(subject().readableFormats()).isEqualTo(formats);
    }

    @ParameterizedTest
    @EnumSource(Fixture.class)
    void readsEveryFixtureAsCatalogued(Fixture fixture) throws Exception {
        Path file = copy(fixture);
        if (fixture.readable()) {
            assertMatches(subject().read(file), FixtureCatalog.require(fixture), fixture);
        } else {
            assertThatThrownBy(() -> subject().read(file))
                    .isInstanceOfSatisfying(MediaAccessException.class, e -> {
                        assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT);
                        assertThat(e.path()).isEqualTo(file);
                    });
        }
    }

    @Test
    void untaggedFileYieldsEmptyLayers() throws Exception {
        TagReadResult result = subject().read(copy(Fixture.NO_TAGS));
        assertThat(result.layers()).isEmpty();
        assertThat(result.effective().isEmpty()).isTrue();
        assertThat(result.stream()).isPresent();
    }

    @Test
    void invalidValueIsOmittedAndReportedWithRawValue() throws Exception {
        TagReadResult result = subject().read(copy(Fixture.V23_BAD_TRACK));
        assertThat(result.effective().get(Field.TRACK)).isEmpty();
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.READ_VALUE_INVALID);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).contains(Field.TRACK);
            assertThat(issue.message()).contains(FixtureCatalog.invalidRawValue(Fixture.V23_BAD_TRACK).orElseThrow());
        });
    }

    @Test
    void missingFileIsNotFound() {
        Path missing = dir.resolve("missing.mp3").toAbsolutePath();
        assertThatThrownBy(() -> subject().read(missing))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void unreadableFileIsAccessDenied() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read anything");
        assumePosix(dir);
        Path file = copy(Fixture.V23_LATIN1);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(file), "file system ignores permissions");
            assertThatThrownBy(() -> subject().read(file))
                    .isInstanceOfSatisfying(MediaAccessException.class,
                            e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ACCESS_DENIED));
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @ParameterizedTest
    @EnumSource(Fixture.class)
    void readingNeverModifiesTheFile(Fixture fixture) throws Exception {
        Path file = copy(fixture);
        String before = Fixtures.sha256(file);
        FileTime mtime = Files.getLastModifiedTime(file);
        try {
            subject().read(file);
        } catch (MediaAccessException expected) {
            assertThat(fixture.readable()).as("only unreadable fixtures may fail").isFalse();
        }
        assertThat(Fixtures.sha256(file)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(mtime);
        assertThat(Fixtures.sha256(file)).isEqualTo(Fixtures.sha256(Fixtures.bytes(fixture)));
    }

    @Test
    void fileIsClosedAfterReading() throws Exception {
        Path file = copy(Fixture.V23_COVER);
        subject().read(file);
        Files.delete(file); // fails on Windows if a handle is still open
        assertThat(file).doesNotExist();
    }

    @Test
    void nonAsciiFileNamesAreReadable() throws Exception {
        Path file = Fixtures.copyTo(Fixture.V23_LATIN1, dir, "Ђорђе – 🎵 čćž.mp3");
        assertMatches(subject().read(file), FixtureCatalog.require(Fixture.V23_LATIN1), Fixture.V23_LATIN1);
    }

    @Test
    void upperCaseExtensionIsReadable() throws Exception {
        Path file = Fixtures.copyTo(Fixture.V23_LATIN1, dir, "SONG.MP3");
        assertMatches(subject().read(file), FixtureCatalog.require(Fixture.V23_LATIN1), Fixture.V23_LATIN1);
    }

    @Test
    void preconditions() throws IOException {
        TagReader reader = subject();
        assertThatThrownBy(() -> reader.read(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reader.read(Path.of("relative.mp3"))).isInstanceOf(IllegalArgumentException.class);
        Path text = Files.writeString(dir.resolve("notes.txt"), "x").toAbsolutePath();
        assertThatThrownBy(() -> reader.read(text)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readCover(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reader.readCover(Path.of("relative.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentReadsGiveTheSameResultsAsSequentialReads() throws Exception {
        TagReader reader = subject();
        Map<Fixture, Path> files = new EnumMap<>(Fixture.class);
        Map<Fixture, TagReadResult> sequential = new EnumMap<>(Fixture.class);
        for (Fixture fixture : Fixture.values()) {
            if (fixture.readable()) {
                Path file = copy(fixture);
                files.put(fixture, file);
                sequential.put(fixture, reader.read(file));
            }
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<Map<Fixture, TagReadResult>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    Map<Fixture, TagReadResult> results = new EnumMap<>(Fixture.class);
                    for (Map.Entry<Fixture, Path> entry : files.entrySet()) {
                        results.put(entry.getKey(), reader.read(entry.getValue()));
                    }
                    return results;
                }));
            }
            for (Future<Map<Fixture, TagReadResult>> future : futures) {
                assertThat(future.get()).isEqualTo(sequential);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Copies a fixture into the temp dir and gives it an old, fixed modification time. */
    protected Path copy(Fixture fixture) throws IOException {
        Path file = Fixtures.copyTo(fixture, dir);
        Files.setLastModifiedTime(file, OLD_MTIME);
        return file;
    }

    /**
     * Compares a read result with the catalogue: format, layers, cover exactly; stream within tolerance; issues by
     * severity, code and field.
     */
    protected static void assertMatches(TagReadResult actual, TagReadResult expected, Fixture fixture) {
        assertThat(actual.format()).as("%s format", fixture).isEqualTo(expected.format());
        assertThat(actual.layers()).as("%s layers", fixture).isEqualTo(expected.layers());
        assertThat(actual.cover()).as("%s cover", fixture).isEqualTo(expected.cover());
        assertThat(actual.stream().isPresent()).as("%s stream present", fixture)
                .isEqualTo(expected.stream().isPresent());
        expected.stream().ifPresent(e -> {
            StreamInfo a = actual.stream().orElseThrow();
            assertThat(a.duration()).as("%s duration", fixture)
                    .isBetween(e.duration().minus(FixtureCatalog.DURATION_TOLERANCE),
                            e.duration().plus(FixtureCatalog.DURATION_TOLERANCE));
            assertThat(a.bitrateKbps()).as("%s bitrate", fixture).isEqualTo(e.bitrateKbps());
            assertThat(a.variableBitrate()).as("%s vbr", fixture).isEqualTo(e.variableBitrate());
        });
        assertThat(actual.issues().stream().map(TagReaderContract::key).toList()).as("%s issues", fixture)
                .containsExactlyInAnyOrderElementsOf(expected.issues().stream().map(TagReaderContract::key).toList());
        if (actual.stream().isPresent() && actual.stream().get().duration().equals(Duration.ZERO)) {
            fail("%s: duration must not be zero for a parsable stream", fixture);
        }
    }

    private static String key(Issue issue) {
        return issue.severity() + " " + issue.code() + " " + issue.field().map(Enum::name).orElse("-");
    }

    /** Skips the current test unless the platform supports POSIX permissions. */
    protected static void assumePosix(Path anyPath) {
        assumeTrue(anyPath.getFileSystem().supportedFileAttributeViews().contains("posix"), "POSIX only");
    }
}
