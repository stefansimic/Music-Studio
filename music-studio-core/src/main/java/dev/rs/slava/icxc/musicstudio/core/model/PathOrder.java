package dev.rs.slava.icxc.musicstudio.core.model;

import java.nio.file.Path;
import java.util.Comparator;

/**
 * The canonical path order used for "sorted by path" (scan) and "auto-number by file name" (extract).
 *
 * <p>Paths are compared element by element: first with {@link String#compareToIgnoreCase}, then with the exact
 * {@link String#compareTo} as a tie-break. A path that is a prefix of another comes first. The order is
 * deterministic on every OS and keeps the files of one folder together.
 */
public final class PathOrder {

    /** The comparator; null-hostile, consistent with {@link Path#equals} for normalised paths. */
    public static final Comparator<Path> COMPARATOR = PathOrder::compare;

    /**
     * Natural order for numbering: like {@link #COMPARATOR}, but runs of ASCII digits are compared by their numeric
     * value ({@code 2 - y} before {@code 10 - x}). Used by auto-numbering by file name.
     */
    public static final Comparator<Path> NATURAL = PathOrder::compareNatural;

    private PathOrder() {
    }

    private static int compare(Path a, Path b) {
        int result = compareText(a.getRoot() == null ? "" : a.getRoot().toString(),
                b.getRoot() == null ? "" : b.getRoot().toString());
        if (result != 0) {
            return result;
        }
        int common = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < common; i++) {
            result = compareText(a.getName(i).toString(), b.getName(i).toString());
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(a.getNameCount(), b.getNameCount());
    }

    private static int compareNatural(Path a, Path b) {
        int result = compareText(a.getRoot() == null ? "" : a.getRoot().toString(),
                b.getRoot() == null ? "" : b.getRoot().toString());
        int common = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < common && result == 0; i++) {
            result = compareNaturalText(a.getName(i).toString(), b.getName(i).toString());
        }
        if (result != 0) {
            return result;
        }
        int lengths = Integer.compare(a.getNameCount(), b.getNameCount());
        return lengths != 0 ? lengths : compare(a, b);
    }

    private static int compareNaturalText(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                String na = stripZeros(a.substring(i, endA));
                String nb = stripZeros(b.substring(j, endB));
                int result = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (result != 0) {
                    return result;
                }
                i = endA;
                j = endB;
            } else {
                int result = Character.compare(Character.toLowerCase(Character.toUpperCase(ca)),
                        Character.toLowerCase(Character.toUpperCase(cb)));
                if (result != 0) {
                    return result;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitsEnd(String s, int from) {
        int end = from;
        while (end < s.length() && isDigit(s.charAt(end))) {
            end++;
        }
        return end;
    }

    private static String stripZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }

    private static int compareText(String a, String b) {
        int result = a.compareToIgnoreCase(b);
        return result != 0 ? result : a.compareTo(b);
    }
}
