package dev.rs.slava.icxc.musicstudio.core.extract;

/** The pattern status of one proposal. */
public enum MatchStatus {
    /** No pattern was given; exportable. */
    NO_PATTERN,
    /** The pattern matched in exactly one way; exportable. */
    MATCHED,
    /** Several splits; the chosen one is shown; not exportable until accepted or fixed. */
    AMBIGUOUS,
    /** Ambiguous, but accepted (CLI flag or GUI); exportable. */
    AMBIGUOUS_ACCEPTED,
    /** The pattern did not match; never written. */
    NO_MATCH;

    /** @return true for the statuses that may be written by 003/004 */
    public boolean exportable() {
        return this == NO_PATTERN || this == MATCHED || this == AMBIGUOUS_ACCEPTED;
    }
}
