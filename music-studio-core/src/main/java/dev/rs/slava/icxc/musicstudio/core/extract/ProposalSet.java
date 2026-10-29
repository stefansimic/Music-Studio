package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * The proposals of a batch.
 *
 * @param proposals one per input file, in input order; not null
 * @param matched   number with status MATCHED or AMBIGUOUS_ACCEPTED
 * @param ambiguous number with status AMBIGUOUS (not accepted)
 * @param noMatch   number with status NO_MATCH
 */
public record ProposalSet(List<Proposal> proposals, int matched, int ambiguous, int noMatch) {

    public ProposalSet {
        Require.noNullElements(proposals, "proposals");
        proposals = List.copyOf(proposals);
        Require.that(matched >= 0 && ambiguous >= 0 && noMatch >= 0 && matched + ambiguous + noMatch <= proposals.size(),
                "the counts do not fit the proposals");
    }
}
