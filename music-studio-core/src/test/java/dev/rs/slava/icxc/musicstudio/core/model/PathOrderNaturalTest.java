package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathOrderNaturalTest {

    private static Path p(String relative) {
        return Path.of("/m").toAbsolutePath().resolve(relative);
    }

    @Test
    void digitRunsAreComparedByValue() {
        List<Path> paths = new ArrayList<>(List.of(p("10 - x.mp3"), p("2 - y.mp3"), p("1 - z.mp3"), p("02b.mp3"),
                p("track10.mp3"), p("Track2.mp3"), p("B/1.mp3"), p("a/3.mp3")));
        paths.sort(PathOrder.NATURAL);
        assertThat(paths).containsExactly(p("1 - z.mp3"), p("2 - y.mp3"), p("02b.mp3"), p("10 - x.mp3"),
                p("a/3.mp3"), p("B/1.mp3"), p("Track2.mp3"), p("track10.mp3"));
    }

    @Test
    void isATotalOrderConsistentWithEquals() {
        assertThat(PathOrder.NATURAL.compare(p("01.mp3"), p("1.mp3"))).isNotZero();
        assertThat(PathOrder.NATURAL.compare(p("a.mp3"), p("A.mp3"))).isNotZero();
        assertThat(PathOrder.NATURAL.compare(p("a.mp3"), p("a.mp3"))).isZero();
        assertThat(Integer.signum(PathOrder.NATURAL.compare(p("01.mp3"), p("1.mp3"))))
                .isEqualTo(-Integer.signum(PathOrder.NATURAL.compare(p("1.mp3"), p("01.mp3"))));
    }
}
