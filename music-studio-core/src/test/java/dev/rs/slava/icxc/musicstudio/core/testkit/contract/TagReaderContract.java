package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
 * Contract of {@link TagReader} for MP3. Every implementation extends this class. Each fixture is copied into a
 * temp directory first; expectations come from {@link FixtureCatalog} (written by hand).
 */
public abstract class TagReaderContract {

    @TempDir
    protected Path tempDir;

    /** @return the implementation under test; a fresh or shared instance, it must be safe for concurrent use */
    protected abstract TagReader subject();

    private Path copy(Fixture fixture) {
        return Fixtures.copyTo(fixture, tempDir);
    }

    private static List<Issue> issueSummary(List<Issue> issues) {
        // message text is implementation specific; compare severity, code and field
        return issues.stream().map(i -> new Issue(i.severity(), i.code(), "-", i.file(), i.field())).toList();
    }

    /** Asserts that {@code actual} is what the catalog expects for the fixture. */
    protected static void assertMatchesCatalog(Fixture fixture, TagReadResult actual) {
        TagReadResult expected = FixtureCatalog.readable(fixture);
        assertThat(actual.format()).as("format of %s", fixture).isEqualTo(expected.format());
        assertThat(actual.layers()).as("layers of %s", fixture).isEqualTo(expected.layers());
        assertThat(actual.cover()).as("cover of %s", fixture).isEqualTo(expected.cover());
        assertThat(issueSummary(actual.issues())).as("issues of %s", fixture).isEqualTo(issueSummary(expected.issues()));
        assertThat(actual.stream()).as("stream of %s", fixture).isPresent();
        StreamInfo stream = actual.stream().orElseThrow();
        assertThat(stream.duration()).as("duration of %s", fixture)
                .isBetween(FixtureCatalog.NOMINAL_STREAM.duration().minus(FixtureCatalog.DURATION_TOLERANCE),
                        FixtureCatalog.NOMINAL_STREAM.duration().plus(FixtureCatalog.DURATION_TOLERANCE));
        assertThat(stream.bitrateKbps()).as("bitrate of %s", fixture).isEqualTo(FixtureCatalog.NOMINAL_STREAM.bitrateKbps());
    }

    @Test
    void readableFormatsAreNonEmptyAndUnmodifiable() {
        assertThat(subject().readableFormats()).isNotEmpty().contains(MediaFormat.MP3);
        assertThatThrownBy(() -> subject().readableFormats().add(MediaFormat.MP3))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Fixture.class, mode = EnumSource.Mode.EXCLUDE, names = {"CORRUPT", "EMPTY"})
    void readsFixtureAsExpected(Fixture fixture) throws Exception {
        assertMatchesCatalog(fixture, subject().read(copy(fixture)));
    }

    @Test
    void fileWithoutTagsHasNoLayers() throws Exception {
        TagReadResult result = subject().read(copy(Fixture.NO_TAGS));
        assertThat(result.layers()).isEmpty();
        assertThat(result.effective().isEmpty()).isTrue();
        assertThat(result.stream()).isPresent();
    }

    @Test
    void invalidTrackIsOmittedAndReported() throws Exception {
        TagReadResult result = subject().read(copy(Fixture.V23_BAD_TRACK));
        assertThat(result.effective().get(Field.TRACK)).isEmpty();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.READ_VALUE_INVALID);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).contains(Field.TRACK);
            assertThat(issue.message()).contains("A1");
        });
    }

    @ParameterizedTest
    @EnumSource(value = Fixture.class, names = {"CORRUPT", "EMPTY"})
    void unreadableFileIsCorrupt(Fixture fixture) {
        Path file = copy(fixture);
        assertThatThrownBy(() -> subject().read(file)).isInstanceOfSatisfying(MediaAccessException.class, e -> {
            assertThat(e.reason()).isEqualTo(Reason.CORRUPT);
            assertThat(e.path()).isEqualTo(file);
        });
    }

    @Test
    void missingFileIsNotFound() {
        Path file = tempDir.resolve("missing.mp3");
        assertThatThrownBy(() -> subject().read(file)).isInstanceOfSatisfying(MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void fileWithoutPermissionIsAccessDenied() throws Exception {
        Path file = copy(Fixture.V23_LATIN1);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        assumeFalse(Files.isReadable(file), "running as root: permissions are not enforced");
        try {
            assertThatThrownBy(() -> subject().read(file)).isInstanceOfSatisfying(MediaAccessException.class,
                    e -> assertThat(e.reason()).isEqualTo(Reason.ACCESS_DENIED));
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @ParameterizedTest
    @EnumSource(Fixture.class)
    void readingNeverModifiesTheFile(Fixture fixture) throws Exception {
        Path file = copy(fixture);
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2019-02-03T04:05:06Z")));
        String hash = Fixtures.sha256(file);
        FileTime mtime = Files.getLastModifiedTime(file);
        try {
            subject().read(file);
        } catch (MediaAccessException expected) {
            // corrupt fixtures fail, they must not be touched either
        }
        assertThat(Fixtures.sha256(file)).isEqualTo(hash);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(mtime);
    }

    @Test
    void nonAsciiFileNamesAreReadable() throws Exception {
        Path file = Fixtures.copyTo(Fixture.V23_LATIN1, tempDir, "Ђорђе – 🎵 čćž.mp3");
        assertMatchesCatalog(Fixture.V23_LATIN1, subject().read(file));
    }

    @Test
    void uppercaseExtensionIsReadable() throws Exception {
        Path file = Fixtures.copyTo(Fixture.V23_LATIN1, tempDir, "LOUD.MP3");
        assertMatchesCatalog(Fixture.V23_LATIN1, subject().read(file));
    }

    @Test
    void preconditions() throws IOException {
        Path text = tempDir.resolve("notes.txt");
        Files.writeString(text, "hello");
        assertThatThrownBy(() -> subject().read(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().read(Path.of("relative.mp3"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject().read(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentReadsGiveTheSameResultsAsSequentialOnes() throws Exception {
        Map<Fixture, Path> files = new EnumMap<>(Fixture.class);
        for (Fixture fixture : FixtureCatalog.readableFixtures()) {
            files.put(fixture, copy(fixture));
        }
        Map<Fixture, TagReadResult> sequential = new EnumMap<>(Fixture.class);
        for (Map.Entry<Fixture, Path> entry : files.entrySet()) {
            sequential.put(entry.getKey(), subject().read(entry.getValue()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Map<Fixture, TagReadResult>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                Callable<Map<Fixture, TagReadResult>> task = () -> {
                    Map<Fixture, TagReadResult> results = new EnumMap<>(Fixture.class);
                    for (Map.Entry<Fixture, Path> entry : files.entrySet()) {
                        results.put(entry.getKey(), subject().read(entry.getValue()));
                    }
                    return results;
                };
                futures.add(pool.submit(task));
            }
            for (Future<Map<Fixture, TagReadResult>> future : futures) {
                assertThat(future.get()).isEqualTo(sequential);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
