package dev.rs.slava.icxc.musicstudio.core.extract;

/** How a file stands in a batch. */
public enum MatchStatus {
    /** No pattern was given; only fixed values, keep and numbering apply. Exportable. */
    NO_PATTERN,
    /** The name fits the pattern in exactly one way. Exportable. */
    MATCHED,
    /** Several ways to split the name; the chosen split is shown. Not exportable until accepted. */
    AMBIGUOUS,
    /** Ambiguous, but accepted by the run or by the user. Exportable. */
    AMBIGUOUS_ACCEPTED,
    /** The name does not fit the pattern. The proposal equals the existing tags; never exportable. */
    NO_MATCH
}
