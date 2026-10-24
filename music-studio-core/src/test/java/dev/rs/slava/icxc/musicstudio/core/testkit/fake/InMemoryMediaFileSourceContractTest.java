package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.MediaFileSourceContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

/** The in-memory fake fulfils the same contract as the local-disk adapter. */
class InMemoryMediaFileSourceContractTest extends MediaFileSourceContract {

    @TempDir
    Path tempDir;

    private InMemoryMediaFileSource source;

    @Override
    protected Tree newTree() {
        source = new InMemoryMediaFileSource();
        Path root = source.addDirectory(tempDir.toAbsolutePath().resolve("root"));
        return new Tree() {
            @Override
            public Path root() {
                return root;
            }

            @Override
            public Path file(String relative, byte[] content, Instant lastModified) {
                return source.addFile(root.resolve(relative), content.length, lastModified);
            }

            @Override
            public Path dir(String relative) {
                return source.addDirectory(root.resolve(relative));
            }

            @Override
            public Path symlink(String relative, String targetRelative) {
                Path link = root.resolve(relative);
                return source.addSymlink(link, link.getParent().resolve(targetRelative).normalize());
            }

            @Override
            public void denyAccess(String relativeDir) {
                source.denyAccess(root.resolve(relativeDir));
            }

            @Override
            public void restoreAccess() {
                source.restoreAccess();
            }

            @Override
            public Map<Path, Instant> snapshot() {
                return source.snapshot();
            }
        };
    }

    @Override
    protected MediaFileSource subject() {
        return source;
    }
}
