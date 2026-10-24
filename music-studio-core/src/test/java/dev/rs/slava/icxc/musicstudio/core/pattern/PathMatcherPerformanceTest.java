package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Propose runs on every keystroke of the GUI pattern field (5 000 files, architecture §12). */
class PathMatcherPerformanceTest {

    @Test
    void fiveThousandNamesWellBelowOneSecond() {
        InputPattern pattern = PatternParserTest.valid("{year} - {album}/{track} - {artist} - {title}");
        Path root = Path.of("/music").toAbsolutePath();
        for (int warmup = 0; warmup < 2; warmup++) {
            long start = System.nanoTime();
            int matched = 0;
            for (int i = 0; i < 5_000; i++) {
                String artist = i % 2 == 0 ? "Ђорђе Балашевић" : "Some Long Artist Name";
                Path file = root.resolve("20%02d - Album Number %d".formatted(i % 100, i / 50))
                        .resolve("%02d - %s - Title of the song number %d with words.mp3".formatted(i % 99 + 1,
                                artist, i));
                if (PathMatcher.match(pattern, file).kind() == MatchKind.MATCHED) {
                    matched++;
                }
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertThat(matched).isEqualTo(5_000);
            if (warmup == 1) {
                assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
            }
        }
    }

    @Test
    void longNameWithManyTokens() {
        InputPattern pattern = PatternParserTest.valid("{track} - {artist} - {album} - {title} - {genre} - {year}");
        String name = "01 - " + "a - ".repeat(20) + "b".repeat(150) + " - Pop - 2020";
        long start = System.nanoTime();
        PathMatcher.match(pattern, Path.of("/m").toAbsolutePath().resolve(name + ".mp3"));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
    }
}
