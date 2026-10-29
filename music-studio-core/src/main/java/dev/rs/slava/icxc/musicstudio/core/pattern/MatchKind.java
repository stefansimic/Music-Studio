package dev.rs.slava.icxc.musicstudio.core.pattern;

/** How a path matched a pattern. */
public enum MatchKind {
    /** Exactly one way to split the name. */
    MATCHED,
    /** Two or more ways to split the name; the shortest-first split is chosen. */
    AMBIGUOUS,
    /** The name does not fit the pattern. */
    NO_MATCH
}
