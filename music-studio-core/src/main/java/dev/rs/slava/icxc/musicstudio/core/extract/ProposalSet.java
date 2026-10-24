package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * The proposals of a batch.
 *
 * @param proposals one per input file, in input order; no nulls; copied
 * @param matched   number of MATCHED proposals
 * @param ambiguous number of AMBIGUOUS and AMBIGUOUS_ACCEPTED proposals
 * @param noMatch   number of NO_MATCH proposals
 */
public record ProposalSet(List<Proposal> proposals, int matched, int ambiguous, int noMatch) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if {@code proposals} or an element is null
     * @throws IllegalArgumentException if a counter does not equal the status count
     */
    public ProposalSet {
        Require.noNullElements(proposals, "proposals");
        Require.that(matched == count(proposals, MatchStatus.MATCHED), "matched does not fit the proposals");
        Require.that(ambiguous == count(proposals, MatchStatus.AMBIGUOUS)
                + count(proposals, MatchStatus.AMBIGUOUS_ACCEPTED), "ambiguous does not fit the proposals");
        Require.that(noMatch == count(proposals, MatchStatus.NO_MATCH), "noMatch does not fit the proposals");
        proposals = List.copyOf(proposals);
    }

    /**
     * @param proposals the proposals; not null
     * @return a set with counters computed from the proposals
     */
    public static ProposalSet of(List<Proposal> proposals) {
        Require.noNullElements(proposals, "proposals");
        return new ProposalSet(proposals, count(proposals, MatchStatus.MATCHED),
                count(proposals, MatchStatus.AMBIGUOUS) + count(proposals, MatchStatus.AMBIGUOUS_ACCEPTED),
                count(proposals, MatchStatus.NO_MATCH));
    }

    /** @return the number of exportable proposals */
    public int exportable() {
        return (int) proposals.stream().filter(Proposal::exportable).count();
    }

    private static int count(List<Proposal> proposals, MatchStatus status) {
        return (int) proposals.stream().filter(p -> p.status() == status).count();
    }
}
