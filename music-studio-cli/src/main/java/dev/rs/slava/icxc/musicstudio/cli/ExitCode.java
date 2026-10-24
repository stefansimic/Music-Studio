package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;

/** CLI exit codes (architecture §11). */
public final class ExitCode {

    /** Success. */
    public static final int OK = 0;
    /** Completed, but some items had errors. */
    public static final int ITEM_ERRORS = 1;
    /** Usage error, invalid input, not executable. */
    public static final int USAGE = 2;
    /** Aborted by an I/O failure (journal, root). */
    public static final int ABORTED = 3;
    /** Cancelled with Ctrl-C. */
    public static final int CANCELLED = 130;

    private ExitCode() {
    }

    /**
     * @param summary a scan summary; not null
     * @return 130 if cancelled; 2 if the folder was not found or is not a folder; 3 if it could not be listed
     *         for another I/O reason; 1 if a file or folder could not be read or a file has an ERROR issue (e.g. a
     *         damaged tag); otherwise 0
     */
    public static int of(ScanSummary summary) {
        Require.nonNull(summary, "summary");
        return switch (summary.status()) {
            case CANCELLED -> CANCELLED;
            case FAILED -> summary.issues().stream().anyMatch(i -> i.code() == IssueCode.ROOT_NOT_FOUND)
                    ? USAGE : ABORTED;
            case COMPLETED -> summary.failed() > 0 || summary.filesWithErrors() > 0
                    || summary.issues().stream().anyMatch(i -> i.severity() != Severity.INFO) ? ITEM_ERRORS : OK;
        };
    }

    /**
     * @param summary   the scan summary; not null
     * @param proposals the proposals of the scanned files; not null
     * @return as {@link #of(ScanSummary)}, plus 1 if a file did not match the pattern or is ambiguous and not
     *         accepted (it would be skipped)
     */
    public static int of(ScanSummary summary, ProposalSet proposals) {
        Require.nonNull(summary, "summary");
        Require.nonNull(proposals, "proposals");
        int scanCode = of(summary);
        if (scanCode != OK) {
            return scanCode;
        }
        boolean skipped = proposals.proposals().stream().anyMatch(p -> !p.exportable());
        return skipped ? ITEM_ERRORS : OK;
    }
}
