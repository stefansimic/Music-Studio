package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathOrderTest {

    private static Path p(String s) {
        return Path.of(s);
    }

    @Test
    void foldersStayGroupedAndCaseIsIgnored() {
        assertThat(PathOrder.COMPARATOR.compare(p("/r/a/B.mp3"), p("/r/a/c.mp3"))).isNegative();
        assertThat(PathOrder.COMPARATOR.compare(p("/r/a/c.mp3"), p("/r/b/a.mp3"))).isNegative();
    }

    @Test
    void caseOnlyDifferenceIsDeterministic() {
        int ab = PathOrder.COMPARATOR.compare(p("/r/A.mp3"), p("/r/a.mp3"));
        int ba = PathOrder.COMPARATOR.compare(p("/r/a.mp3"), p("/r/A.mp3"));
        assertThat(ab).isNotZero();
        assertThat(Integer.signum(ab)).isEqualTo(-Integer.signum(ba));
    }

    @Test
    void folderContentIsNotInterleavedWithSiblings() {
        List<Path> sorted = new ArrayList<>(List.of(p("/r/a-b.mp3"), p("/r/a/z.mp3"), p("/r/a/b.mp3"), p("/r/a.mp3")));
        sorted.sort(PathOrder.COMPARATOR);
        assertThat(sorted).containsExactly(p("/r/a/b.mp3"), p("/r/a/z.mp3"), p("/r/a-b.mp3"), p("/r/a.mp3"));
    }

    @Test
    void equalPathsCompareEqual() {
        assertThat(PathOrder.COMPARATOR.compare(p("/r/x.mp3"), p("/r/x.mp3"))).isZero();
    }

    @Test
    void sortingIsIndependentOfInputOrder() {
        List<Path> paths = List.of(p("/r/b/a.mp3"), p("/r/A.mp3"), p("/r/a.mp3"), p("/r/a/c.mp3"), p("/r/a/B.mp3"));
        List<Path> one = new ArrayList<>(paths);
        List<Path> two = new ArrayList<>(paths);
        Collections.reverse(two);
        one.sort(PathOrder.COMPARATOR);
        two.sort(PathOrder.COMPARATOR);
        assertThat(one).isEqualTo(two);
    }
}
