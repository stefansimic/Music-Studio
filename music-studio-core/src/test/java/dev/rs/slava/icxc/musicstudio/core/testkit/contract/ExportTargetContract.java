package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract of {@link ExportTarget} (spec X02). {@link #create} lets each implementation make a file visible to the
 * subject (a real file for the local one, an in-memory entry for the fake).
 */
public abstract class ExportTargetContract {

    @TempDir
    protected Path tempDir;

    protected abstract ExportTarget subject();

    /** Makes a file with {@code content} exist at {@code path}, as seen by {@link #subject()}. */
    protected abstract void create(Path path, String content) throws IOException;

    /** @return the content of the file at {@code path}, as seen by the subject */
    protected abstract String content(Path path) throws IOException;

    @Test
    void existsReflectsFiles() throws IOException {
        Path file = tempDir.resolve("a.mp3");
        assertThat(subject().exists(file)).isFalse();
        create(file, "x");
        assertThat(subject().exists(file)).isTrue();
    }

    @Test
    void existsRejectsNullAndRelative() {
        assertThatThrownBy(() -> subject().exists(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().exists(Path.of("a.mp3"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservedTempPathIsSiblingHiddenSuffixedUniqueAndNotCreated() {
        Path target = tempDir.resolve("song.mp3");
        Set<Path> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Path temp = subject().reserveTempPath(target);
            assertThat(temp.getParent()).isEqualTo(tempDir);
            assertThat(temp.getFileName().toString()).startsWith(".").contains(ExportTarget.TEMP_MARKER);
            assertThat(subject().exists(temp)).isFalse();
            assertThat(seen.add(temp)).as("unique").isTrue();
        }
    }

    @Test
    void reserveRejectsNullAndRelative() {
        assertThatThrownBy(() -> subject().reserveTempPath(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().reserveTempPath(Path.of("a.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveNoReplaceMovesContentAndRemovesTemp() throws Exception {
        Path target = tempDir.resolve("song.mp3");
        Path temp = subject().reserveTempPath(target);
        create(temp, "payload");
        subject().moveNoReplace(temp, target);
        assertThat(content(target)).isEqualTo("payload");
        assertThat(subject().exists(temp)).isFalse();
    }

    @Test
    void moveNoReplaceNeverReplacesAnExistingFile() throws Exception {
        Path target = tempDir.resolve("song.mp3");
        create(target, "original");
        Path temp = subject().reserveTempPath(target);
        create(temp, "new");
        assertThatThrownBy(() -> subject().moveNoReplace(temp, target)).isInstanceOfSatisfying(
                MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ALREADY_EXISTS));
        assertThat(content(target)).isEqualTo("original");
    }

    @Test
    void moveRejectsNonTempNamesNullAndRelative() throws Exception {
        Path notTemp = tempDir.resolve("plain.mp3");
        create(notTemp, "x");
        Path target = tempDir.resolve("t.mp3");
        assertThatThrownBy(() -> subject().moveNoReplace(notTemp, target))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(content(notTemp)).isEqualTo("x");
        assertThatThrownBy(() -> subject().moveNoReplace(null, target)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().moveNoReplace(notTemp, null)).isInstanceOf(NullPointerException.class);
        Path temp = subject().reserveTempPath(target);
        assertThatThrownBy(() -> subject().moveNoReplace(temp, Path.of("rel.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The port Javadoc lists a missing temp both as precondition (IAE) and as NOT_FOUND: either is accepted. */
    @Test
    void moveOfMissingTempFailsAndCreatesNoTarget() {
        Path target = tempDir.resolve("t.mp3");
        Path temp = subject().reserveTempPath(target);
        assertThatThrownBy(() -> subject().moveNoReplace(temp, target)).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(IllegalArgumentException.class),
                e -> assertThat(e).isInstanceOfSatisfying(MediaAccessException.class,
                        x -> assertThat(x.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND)));
        assertThat(subject().exists(target)).isFalse();
    }

    @Test
    void deleteTempRemovesOnlyTempFilesAndToleratesMissing() throws Exception {
        Path target = tempDir.resolve("t.mp3");
        Path temp = subject().reserveTempPath(target);
        subject().deleteTemp(temp); // missing: no-op
        create(temp, "x");
        subject().deleteTemp(temp);
        assertThat(subject().exists(temp)).isFalse();
    }

    @Test
    void deleteTempRefusesOtherFiles() throws Exception {
        Path precious = tempDir.resolve("precious.mp3");
        create(precious, "keep");
        assertThatThrownBy(() -> subject().deleteTemp(precious)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject().deleteTemp(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject().deleteTemp(Path.of("x" + ExportTarget.TEMP_MARKER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(content(precious)).isEqualTo("keep");
    }
}
