package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.MediaFileSourceContract;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

/** The local-disk adapter fulfils the {@link MediaFileSource} contract on the real file system. */
class LocalMediaFileSourceTest extends MediaFileSourceContract {

    @TempDir
    Path tempDir;

    @Override
    protected Tree newTree() {
        return new DiskTree(tempDir.toAbsolutePath().resolve("root"));
    }

    @Override
    protected MediaFileSource subject() {
        return new LocalMediaFileSource();
    }

    @Override
    protected boolean supportsSymlinks() {
        try {
            Path probe = tempDir.resolve("symlink-probe");
            Files.createSymbolicLink(probe, tempDir);
            Files.delete(probe);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    protected boolean supportsAccessDenial() {
        return !"root".equals(System.getProperty("user.name"))
                && tempDir.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** Builds the test tree on disk. */
    static final class DiskTree implements Tree {
        private final Path root;
        private final List<Path> denied = new ArrayList<>();

        DiskTree(Path root) {
            this.root = root;
            io(() -> Files.createDirectories(root));
        }

        @Override
        public Path root() {
            return root;
        }

        @Override
        public Path file(String relative, byte[] content, Instant lastModified) {
            Path file = root.resolve(relative);
            io(() -> {
                Files.createDirectories(file.getParent());
                Files.write(file, content);
                Files.setLastModifiedTime(file, FileTime.from(lastModified));
            });
            return file;
        }

        @Override
        public Path dir(String relative) {
            Path dir = root.resolve(relative);
            io(() -> Files.createDirectories(dir));
            return dir;
        }

        @Override
        public Path symlink(String relative, String targetRelative) {
            Path link = root.resolve(relative);
            io(() -> {
                Files.createDirectories(link.getParent());
                Files.createSymbolicLink(link, link.getParent().resolve(targetRelative).normalize());
            });
            return link;
        }

        @Override
        public void denyAccess(String relativeDir) {
            Path dir = root.resolve(relativeDir);
            io(() -> Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------")));
            denied.add(dir);
        }

        @Override
        public void restoreAccess() {
            for (Path dir : denied) {
                io(() -> Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------")));
            }
            denied.clear();
        }

        @Override
        public Map<Path, Instant> snapshot() {
            Map<Path, Instant> snapshot = new HashMap<>();
            io(() -> Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    snapshot.put(dir, attrs.lastModifiedTime().toInstant());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    snapshot.put(file, Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    snapshot.put(file, Instant.EPOCH);
                    return FileVisitResult.CONTINUE;
                }
            }));
            return snapshot;
        }

        private interface IoAction {
            void run() throws IOException;
        }

        private static void io(IoAction action) {
            try {
                action.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
