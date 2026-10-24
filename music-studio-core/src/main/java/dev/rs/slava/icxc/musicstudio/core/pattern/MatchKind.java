package dev.rs.slava.icxc.musicstudio.core.pattern;

/** How a path matched an input pattern. */
public enum MatchKind {
    /** Exactly one split. */
    MATCHED,
    /** Several splits with different values; the non-greedy one is chosen. */
    AMBIGUOUS,
    /** No split. */
    NO_MATCH
}
