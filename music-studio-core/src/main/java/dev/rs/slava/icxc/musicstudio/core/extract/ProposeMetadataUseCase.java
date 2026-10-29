package dev.rs.slava.icxc.musicstudio.core.extract;

/**
 * Computes the proposed final metadata for a batch: pattern extraction, fixed values, keep-existing, merge mode,
 * auto-numbering and manual edits. Pure: no I/O, writes nothing, fast enough to run on every keystroke (5 000 files).
 *
 * <p><b>Thread-safety:</b> stateless; safe for concurrent use.
 */
public interface ProposeMetadataUseCase {

    /**
     * <p><b>Preconditions:</b> {@code request} not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>One {@link Proposal} per input file, in input order.</li>
     *   <li>Per field the value comes from, later wins: the file's current tags, the pattern value (subject to the
     *       merge mode), the fixed value (subject to the merge mode), the auto-number, the manual edit (always wins).
     *       {@code KeepExisting} pins a field to its current value and ignores pattern and fixed value for it.</li>
     *   <li>{@code FILL_EMPTY}: a pattern or fixed value is only used if the field is empty now.</li>
     *   <li>Status: NO_PATTERN (no pattern), MATCHED, AMBIGUOUS (several splits; the non-greedy split is chosen and
     *       shown; not exportable), AMBIGUOUS_ACCEPTED (spec.acceptAmbiguous or a manual accept), NO_MATCH
     *       ({@code proposed} equals the current tags: no fixed value, no number and no edit applies;
     *       {@code matchDetail} says why).</li>
     *   <li>{@code changes} lists exactly the fields where proposed differs from the current tags, with origin.</li>
     *   <li>A captured part that cannot be parsed for its field never reaches {@code proposed}: the match fails.</li>
     *   <li>Auto-numbering numbers exportable files per folder from 1 in the configured order; a track number that the
     *       pattern, a fixed value or keep-existing provided wins; with {@code setTotal} the folder's count is the
     *       total.</li>
     *   <li>Nothing is read from or written to disk.</li>
     * </ul>
     *
     * @param request what to propose; not null
     * @return the proposals; never null
     */
    ProposalSet propose(ProposeRequest request);
}
