package dev.rs.slava.icxc.musicstudio.core.model;

import java.nio.file.Path;
import java.util.Comparator;

/**
 * The one ordering for "sorted by path": element by element, first ignoring case, then exact as a tie-break.
 * Deterministic on every OS; keeps the content of a folder together.
 */
public final class PathOrder {

    /** Compares absolute or relative paths; never returns 0 for different paths. */
    public static final Comparator<Path> COMPARATOR = PathOrder::compare;

    private PathOrder() {}

    private static int compare(Path a, Path b) {
        String rootA = a.getRoot() == null ? "" : a.getRoot().toString();
        String rootB = b.getRoot() == null ? "" : b.getRoot().toString();
        int result = rootA.compareToIgnoreCase(rootB);
        if (result != 0) {
            return result;
        }
        int common = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < common; i++) {
            result = a.getName(i).toString().compareToIgnoreCase(b.getName(i).toString());
            if (result != 0) {
                return result;
            }
        }
        result = Integer.compare(a.getNameCount(), b.getNameCount());
        return result != 0 ? result : a.toString().compareTo(b.toString());
    }
}
