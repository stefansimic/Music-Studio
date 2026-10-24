package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathOrderTest {

    private static Path p(String relative) {
        return Path.of("/music").toAbsolutePath().resolve(relative);
    }

    @Test
    void ordersElementByElementIgnoringCaseFirst() {
        List<Path> expected = List.of(p("a/B.mp3"), p("a/c.mp3"), p("b/a.mp3"));
        List<Path> shuffled = new ArrayList<>(expected);
        Collections.reverse(shuffled);
        shuffled.sort(PathOrder.COMPARATOR);
        assertThat(shuffled).containsExactlyElementsOf(expected);
    }

    @Test
    void caseOnlyDifferencesAreDeterministic() {
        assertThat(PathOrder.COMPARATOR.compare(p("A.mp3"), p("a.mp3"))).isNegative();
        assertThat(PathOrder.COMPARATOR.compare(p("a.mp3"), p("A.mp3"))).isPositive();
        assertThat(PathOrder.COMPARATOR.compare(p("a.mp3"), p("a.mp3"))).isZero();
    }

    @Test
    void foldersStayGrouped() {
        // "a b/z" must not end up between "a/x" and "a/y" (plain string order would put ' ' before '/')
        List<Path> paths = new ArrayList<>(List.of(p("a/y.mp3"), p("a b/z.mp3"), p("a/x.mp3")));
        paths.sort(PathOrder.COMPARATOR);
        assertThat(paths).containsExactly(p("a/x.mp3"), p("a/y.mp3"), p("a b/z.mp3"));
    }

    @Test
    void parentBeforeChild() {
        assertThat(PathOrder.COMPARATOR.compare(p("a"), p("a/b"))).isNegative();
    }
}
