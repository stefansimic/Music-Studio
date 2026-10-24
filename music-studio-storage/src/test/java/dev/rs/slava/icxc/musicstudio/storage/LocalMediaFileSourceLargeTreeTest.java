package dev.rs.slava.icxc.musicstudio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaFileSourceLargeTreeTest {

    @TempDir
    Path tempDir;

    @Test
    void listsTenThousandFilesQuickly() throws Exception {
        Path root = tempDir.toAbsolutePath();
        for (int d = 0; d < 100; d++) {
            Path dir = Files.createDirectories(root.resolve("album-%03d".formatted(d)));
            for (int f = 0; f < 100; f++) {
                Files.createFile(dir.resolve("%03d.mp3".formatted(f)));
            }
        }
        long start = System.nanoTime();
        DirectoryListing listing = new LocalMediaFileSource()
                .list(root, new ListingOptions(true, false, Set.of()), Cancellation.NONE);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(listing.files()).hasSize(10_000);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }
}
