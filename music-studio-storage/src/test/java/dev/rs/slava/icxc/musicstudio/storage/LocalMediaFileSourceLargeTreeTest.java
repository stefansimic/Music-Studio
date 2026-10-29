package dev.rs.slava.icxc.musicstudio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Streaming-size sanity: 10 000 files in 100 folders. */
class LocalMediaFileSourceLargeTreeTest {

    @TempDir
    Path tempDir;

    @Test
    void listsTenThousandFilesQuickly() throws Exception {
        for (int folder = 0; folder < 100; folder++) {
            Path dir = Files.createDirectories(tempDir.resolve("folder" + folder));
            for (int i = 0; i < 100; i++) {
                createEmpty(dir.resolve("track" + i + ".mp3"));
            }
        }
        long start = System.nanoTime();
        var listing = new LocalMediaFileSource().list(tempDir, ListingOptions.defaults().withRecursive(true),
                Cancellation.NONE);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(listing.files()).hasSize(10_000);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    private static void createEmpty(Path file) throws IOException {
        Files.createFile(file);
    }
}
