package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable form of the {@link ExportTarget} Javadoc contract on real files in a {@code @TempDir}. */
public abstract class ExportTargetContract {

    @TempDir
    protected Path dir;

    /** @return the target under test */
    protected abstract ExportTarget subject();

    private Path out() throws Exception {
        return Files.createDirectories(dir.toAbsolutePath().resolve("out"));
    }

    @Test
    void findCollisionIgnoresCaseAndNormalisation() throws Exception {
        Path out = out();
        Path existing = Files.writeString(out.resolve("Song.mp3"), "x");
        assertThat(subject().findCollision(out.resolve("Song.mp3"))).contains(existing);
        assertThat(subject().findCollision(out.resolve("song.MP3"))).contains(existing);
        Path cafe = Files.writeString(out.resolve("Café.mp3"), "x");
        assertThat(subject().findCollision(out.resolve(Normalizer.normalize("Café.mp3", Normalizer.Form.NFD))))
                .contains(cafe);
        assertThat(subject().findCollision(out.resolve("Other.mp3"))).isEmpty();
        assertThat(subject().findCollision(out.resolve("missing/x.mp3"))).isEmpty();
        assertThat(Files.readString(existing)).isEqualTo("x");
    }

    @Test
    void createDirectoriesReturnsWhatItCreated() throws Exception {
        Path base = dir.toAbsolutePath();
        List<Path> created = subject().createDirectories(base.resolve("a/b/c"));
        assertThat(created).containsExactly(base.resolve("a"), base.resolve("a/b"), base.resolve("a/b/c"));
        assertThat(subject().createDirectories(base.resolve("a/b/c"))).isEmpty();
        Files.writeString(base.resolve("file"), "x");
        assertThatThrownBy(() -> subject().createDirectories(base.resolve("file/sub")))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ALREADY_EXISTS));
    }

    @Test
    void reserveTempPathCreatesNothing() throws Exception {
        Path out = out();
        Path temp = subject().reserveTempPath(out.resolve("Song.mp3"));
        assertThat(temp.getParent()).isEqualTo(out);
        assertThat(temp.getFileName().toString()).startsWith(".Song.mp3.").endsWith(ExportTarget.TEMP_SUFFIX);
        assertThat(temp).doesNotExist();
        assertThat(subject().reserveTempPath(out.resolve("Song.mp3"))).isNotEqualTo(temp);
        String longName = "x".repeat(250) + ".mp3";
        assertThat(subject().reserveTempPath(out.resolve(longName)).getFileName().toString().length())
                .isLessThanOrEqualTo(255);
        try (var files = Files.list(out)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void moveNoReplaceKeepsContentAndTime() throws Exception {
        Path out = out();
        Path target = out.resolve("Song.mp3");
        Path temp = subject().reserveTempPath(target);
        Files.writeString(temp, "content");
        FileTime time = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(temp, time);
        subject().moveNoReplace(temp, target);
        assertThat(target).hasContent("content");
        assertThat(Files.getLastModifiedTime(target)).isEqualTo(time);
        assertThat(temp).doesNotExist();
    }

    @Test
    void moveNeverReplaces() throws Exception {
        Path out = out();
        Path target = Files.writeString(out.resolve("Song.mp3"), "original");
        Path temp = subject().reserveTempPath(target);
        Files.writeString(temp, "new");
        assertThatThrownBy(() -> subject().moveNoReplace(temp, target))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ALREADY_EXISTS));
        assertThat(target).hasContent("original");
        assertThat(temp).exists();
        assertThatThrownBy(() -> subject().moveNoReplace(temp, dir.toAbsolutePath().resolve("elsewhere.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteTempOnlyDeletesTempFiles() throws Exception {
        Path out = out();
        Path temp = subject().reserveTempPath(out.resolve("Song.mp3"));
        Files.writeString(temp, "x");
        subject().deleteTemp(temp);
        assertThat(temp).doesNotExist();
        subject().deleteTemp(temp); // missing: no error
        Path user = Files.writeString(out.resolve("song.mp3"), "keep");
        assertThatThrownBy(() -> subject().deleteTemp(user)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user).hasContent("keep");
    }

    @Test
    void fingerprint() throws Exception {
        Path file = Files.write(out().resolve("f.mp3"), new byte[42]);
        Instant time = Instant.parse("2020-01-01T00:00:00Z");
        Files.setLastModifiedTime(file, FileTime.from(time));
        assertThat(subject().fingerprint(file)).isEqualTo(new FileFingerprint(42, time));
        assertThatThrownBy(() -> subject().fingerprint(dir.toAbsolutePath().resolve("missing.mp3")))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
    }

    @Test
    void preconditions() {
        ExportTarget target = subject();
        assertThatThrownBy(() -> target.findCollision(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> target.findCollision(Path.of("rel.mp3"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> target.createDirectories(Path.of("rel"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> target.reserveTempPath(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> target.deleteTemp(Path.of("rel" + ExportTarget.TEMP_SUFFIX)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> target.fingerprint(Path.of("rel.mp3"))).isInstanceOf(IllegalArgumentException.class);
    }
}
