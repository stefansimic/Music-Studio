package dev.rs.slava.icxc.musicstudio.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ExportTargetContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** X02 (contract), D05 (never overwrite, race), E02 (unwritable folder), E17 (non-ASCII names). */
class LocalExportTargetTest extends ExportTargetContract {

    private final LocalExportTarget target = new LocalExportTarget();

    @Override
    protected ExportTarget subject() {
        return target;
    }

    @Override
    protected void create(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    @Override
    protected String content(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    void failedMoveLeavesTargetAndTempUntouched() throws Exception {
        Path destination = tempDir.resolve("song.mp3");
        Files.writeString(destination, "original");
        Path temp = target.reserveTempPath(destination);
        Files.writeString(temp, "new");
        assertThatThrownBy(() -> target.moveNoReplace(temp, destination)).isInstanceOfSatisfying(
                MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ALREADY_EXISTS));
        assertThat(Files.readString(destination)).isEqualTo("original");
        assertThat(Files.readString(temp)).isEqualTo("new");
    }

    @Test
    void existingSymlinkAtTheTargetIsNeverFollowedOrReplaced() throws Exception {
        Path precious = tempDir.resolve("precious.mp3");
        Files.writeString(precious, "keep");
        Path link = tempDir.resolve("link.mp3");
        try {
            Files.createSymbolicLink(link, precious);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not available");
        }
        Path temp = target.reserveTempPath(link);
        Files.writeString(temp, "new");
        assertThatThrownBy(() -> target.moveNoReplace(temp, link)).isInstanceOf(MediaAccessException.class);
        assertThat(Files.readString(precious)).isEqualTo("keep");
        assertThat(Files.isSymbolicLink(link)).isTrue();
    }

    @Test
    void danglingSymlinkAtTheTargetIsNotReplacedEither() throws Exception {
        Path link = tempDir.resolve("dangling.mp3");
        try {
            Files.createSymbolicLink(link, tempDir.resolve("nowhere.mp3"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not available");
        }
        Path temp = target.reserveTempPath(link);
        Files.writeString(temp, "new");
        assertThatThrownBy(() -> target.moveNoReplace(temp, link)).isInstanceOf(MediaAccessException.class);
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(tempDir.resolve("nowhere.mp3")).doesNotExist();
    }

    @Test
    void raceBetweenTwoMovesHasExactlyOneWinnerAndNeverCorruptsTheContent() throws Exception {
        for (int round = 0; round < 25; round++) {
            Path destination = tempDir.resolve("race-" + round + ".mp3");
            Path first = target.reserveTempPath(destination);
            Path second = target.reserveTempPath(destination);
            Files.writeString(first, "first");
            Files.writeString(second, "second");
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Boolean>> tasks = new ArrayList<>();
                for (Path temp : List.of(first, second)) {
                    tasks.add(() -> {
                        start.await();
                        try {
                            target.moveNoReplace(temp, destination);
                            return true;
                        } catch (MediaAccessException e) {
                            assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ALREADY_EXISTS);
                            return false;
                        }
                    });
                }
                List<Future<Boolean>> futures = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    futures.add(pool.submit(task));
                }
                start.countDown();
                int winners = 0;
                for (Future<Boolean> future : futures) {
                    if (future.get()) {
                        winners++;
                    }
                }
                assertThat(winners).as("round %d", round).isEqualTo(1);
                assertThat(Files.readString(destination)).isIn("first", "second");
                Path loser = Files.readString(destination).equals("first") ? second : first;
                assertThat(loser).exists(); // the loser's temp is left for the caller to delete
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void unwritableFolderGivesAccessDeniedAndLeavesNoTargetOrTemp() throws Exception {
        Path source = tempDir.resolve("src");
        Path locked = tempDir.resolve("locked");
        Files.createDirectory(source);
        Files.createDirectory(locked);
        Path temp = source.resolve(".x.1" + ExportTarget.TEMP_MARKER);
        Files.writeString(temp, "x");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(locked), "running as root: permissions are not enforced");
            Path destination = locked.resolve("song.mp3");
            assertThatThrownBy(() -> target.moveNoReplace(temp, destination)).isInstanceOfSatisfying(
                    MediaAccessException.class,
                    e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ACCESS_DENIED));
            try (var list = Files.list(locked)) {
                assertThat(list.toList()).isEmpty();
            }
            assertThat(temp).exists();
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void deleteTempRemovesALinkNamedLikeATempFileButNeverItsTarget() throws Exception {
        Path precious = tempDir.resolve("precious.mp3");
        Files.writeString(precious, "keep");
        Path link = tempDir.resolve(".evil" + ExportTarget.TEMP_MARKER);
        try {
            Files.createSymbolicLink(link, precious);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not available");
        }
        target.deleteTemp(link);
        assertThat(Files.readString(precious)).isEqualTo("keep");
    }

    @Test
    void nonAsciiNamesWork() throws Exception {
        Path destination = tempDir.resolve("Ђорђе – 🎵.mp3");
        try {
            Files.writeString(destination, "probe");
            Files.delete(destination);
        } catch (java.nio.file.InvalidPathException | IOException e) {
            assumeTrue(false, "file system or locale cannot hold non-ASCII names");
        }
        Path temp = target.reserveTempPath(destination);
        Files.writeString(temp, "payload");
        target.moveNoReplace(temp, destination);
        assertThat(Files.readString(destination)).isEqualTo("payload");
        assertThat(temp).doesNotExist();
    }

    @Test
    void veryLongAndSurrogateSplittingNamesStillGetAUsableTempPath() throws Exception {
        String longName = "a".repeat(79) + "🎵" + "b".repeat(100) + ".mp3"; // cut at 80 chars splits the emoji
        Path destination = tempDir.resolve(longName);
        try {
            Files.writeString(destination, "probe");
            Files.delete(destination);
        } catch (java.nio.file.InvalidPathException | IOException e) {
            assumeTrue(false, "file system or locale cannot hold this name");
        }
        Path temp = target.reserveTempPath(destination);
        assertThat(temp.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(255);
        Files.writeString(temp, "x");
        target.moveNoReplace(temp, destination);
        assertThat(destination).exists();
    }

    @Test
    void tempNameOfMultiByteNamesStaysWithinTheFilenameLimit() throws Exception {
        Path destination = tempDir.resolve("日".repeat(80) + ".mp3"); // 252 bytes: a legal file name
        try {
            Files.writeString(destination, "probe");
            Files.delete(destination);
        } catch (java.nio.file.InvalidPathException | IOException e) {
            assumeTrue(false, "file system or locale cannot hold this name");
        }
        Path temp = target.reserveTempPath(destination);
        assertThat(temp.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(255);
        Files.writeString(temp, "x");
        target.moveNoReplace(temp, destination);
        assertThat(destination).exists();
    }

    @Test
    void existsIsTrueForDirectoriesAndFalseForMissingFolders() throws Exception {
        assertThat(target.exists(tempDir)).isTrue();
        assertThat(target.exists(tempDir.resolve("nope").resolve("x.mp3"))).isFalse();
    }
}
