package dev.rs.slava.icxc.musicstudio.core.extract;

/**
 * Computes the proposed final metadata for a batch: pattern extraction, fixed values, keep-existing, merge mode,
 * auto-numbering and manual edits (later also cleanup 005 and genre normalisation 008). Pure: no I/O, no file is
 * read or written; fast enough to run on every keystroke of the GUI pattern field (5 000 files).
 *
 * <p><b>Thread-safety:</b> stateless; safe for concurrent use.
 */
public interface ProposeMetadataUseCase {

    /**
     * Proposes metadata.
     *
     * <p><b>Preconditions:</b> request not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>One {@link Proposal} per input file, in input order.</li>
     *   <li>Per field, the value is derived in this order (later wins): existing effective value → pattern value
     *       (subject to merge mode) → fixed value (subject to merge mode) → auto-number (subject to merge mode) →
     *       manual edit (always wins). KeepExisting pins a field to its existing value and ignores pattern/fixed
     *       for that field.</li>
     *   <li>MergeMode.FILL_EMPTY: a pattern/fixed/auto-number value is only used if the existing value is
     *       absent.</li>
     *   <li>status: NO_PATTERN, MATCHED, AMBIGUOUS (non-greedy split chosen and shown, {@code alternative} = the
     *       other split), AMBIGUOUS_ACCEPTED (spec.acceptAmbiguous or manually accepted), NO_MATCH (proposed ==
     *       existing; fixed values and manual edits are not applied; matchDetail says why). Only NO_PATTERN,
     *       MATCHED and AMBIGUOUS_ACCEPTED are exportable.</li>
     *   <li>{@code extracted} holds the raw text of every placeholder, also for fields that KeepExisting or
     *       FILL_EMPTY did not use.</li>
     *   <li>{@code changes} lists exactly the fields where proposed differs from existing, with origin.</li>
     *   <li>Issues: NO_MATCH (ERROR), AMBIGUOUS_MATCH (ERROR, or WARNING when accepted), POSITION_EXCEEDS_TOTAL
     *       (WARNING, only for a changed track/disc), DUPLICATE_TRACK_NUMBER (WARNING, auto-numbering with
     *       FILL_EMPTY gave a number that an existing track already has); each with the file.</li>
     *   <li>Manual edits of an AMBIGUOUS file are applied, but the file stays not exportable until it is accepted
     *       ({@link ManualEdits#acceptedAmbiguous()}): fixing values and accepting the split are separate steps.</li>
     *   <li>Auto-numbering numbers the exportable files per parent folder in the configured order, starting at 1;
     *       total = number of those files in the folder if setTotal.</li>
     * </ul>
     *
     * @param request what to propose; not null
     * @return the proposals; never null
     * @throws NullPointerException if {@code request} is null
     */
    ProposalSet propose(ProposeRequest request);
}
