package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TestTree;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** {@link TestTree} that builds into an {@link InMemoryMediaFileSource}. Nothing is written to disk. */
public final class InMemoryTree implements TestTree {

    private final Path root;
    private final InMemoryMediaFileSource source;
    private int counter;

    /** @param root absolute root path (need not exist on disk) */
    public InMemoryTree(Path root, InMemoryMediaFileSource source) {
        this.root = root.toAbsolutePath().normalize();
        this.source = source;
        source.addDirectory(this.root);
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public Path file(String relative, int sizeBytes) {
        Path path = root.resolve(relative);
        // distinct, increasing mtimes so that a changed fingerprint is noticed
        source.addFile(path, new FileFingerprint(sizeBytes, Instant.parse("2021-03-04T05:06:07Z").plusSeconds(counter++)));
        return path;
    }

    @Override
    public Path directory(String relative) {
        Path path = root.resolve(relative);
        source.addDirectory(path);
        return path;
    }

    @Override
    public Optional<Path> symlinkToFile(String relative, String targetRelative) {
        Path link = root.resolve(relative);
        try {
            source.addFileLink(link, source.fingerprint(root.resolve(targetRelative)));
        } catch (Exception e) {
            throw new IllegalArgumentException("link target does not exist: " + targetRelative, e);
        }
        return Optional.of(link);
    }

    @Override
    public Optional<Path> symlinkToDirectory(String relative, String targetRelative) {
        Path link = root.resolve(relative);
        source.addDirectoryLink(link);
        return Optional.of(link);
    }

    @Override
    public boolean denyAccess(String directoryRelative) {
        source.denyAccess(root.resolve(directoryRelative), true);
        return true;
    }

    @Override
    public void restoreAccess(String directoryRelative) {
        source.denyAccess(root.resolve(directoryRelative), false);
    }

    @Override
    public FileFingerprint expectedFingerprint(Path file) {
        try {
            return source.fingerprint(file);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Map<String, String> snapshot() {
        return source.describe();
    }
}
