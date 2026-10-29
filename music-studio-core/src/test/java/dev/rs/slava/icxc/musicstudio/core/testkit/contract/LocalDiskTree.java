package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/** {@link TestTree} on the real file system below a temp directory. */
public final class LocalDiskTree implements TestTree {

    private final Path root;

    /** @param root an existing, empty temp directory (absolute) */
    public LocalDiskTree(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public Path file(String relative, int sizeBytes) {
        Path path = root.resolve(relative);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, new byte[sizeBytes]);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path directory(String relative) {
        try {
            return Files.createDirectories(root.resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Path> symlinkToFile(String relative, String targetRelative) {
        return symlink(relative, targetRelative);
    }

    @Override
    public Optional<Path> symlinkToDirectory(String relative, String targetRelative) {
        return symlink(relative, targetRelative);
    }

    private Optional<Path> symlink(String relative, String targetRelative) {
        Path link = root.resolve(relative);
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, root.resolve(targetRelative));
            return Optional.of(link);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean denyAccess(String directoryRelative) {
        Path dir = root.resolve(directoryRelative);
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------"));
            return !Files.isReadable(dir); // still readable when running as root
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public void restoreAccess(String directoryRelative) {
        try {
            Files.setPosixFilePermissions(root.resolve(directoryRelative), PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException | UnsupportedOperationException e) {
            // nothing to restore
        }
    }

    @Override
    public FileFingerprint expectedFingerprint(Path file) {
        try {
            return new FileFingerprint(Files.size(file), Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(path -> {
                try {
                    String kind = Files.isSymbolicLink(path) ? "link" : Files.isDirectory(path) ? "dir" : "file";
                    long size = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path) : -1;
                    result.put(root.relativize(path).toString(), kind + " " + size + " "
                            + Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}
