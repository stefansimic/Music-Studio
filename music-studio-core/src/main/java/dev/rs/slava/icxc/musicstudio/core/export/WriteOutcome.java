package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import java.util.Optional;

/** What apply did with one planned item. */
public sealed interface WriteOutcome {

    /** @return the planned item; never null */
    PlannedWrite planned();

    /**
     * The new file was written, re-read and matches the plan.
     *
     * @param planned           not null
     * @param targetFingerprint of the new file; empty if it could not be read after the move (the file is complete); not null
     */
    record Written(PlannedWrite planned, Optional<FileFingerprint> targetFingerprint) implements WriteOutcome {
        public Written {
            Require.nonNull(planned, "planned");
            Require.nonNull(targetFingerprint, "targetFingerprint");
        }
    }

    /**
     * Nothing was written for this item and no target file or temp file is left.
     *
     * @param planned not null
     * @param error   why; ERROR severity; not null
     */
    record Failed(PlannedWrite planned, Issue error) implements WriteOutcome {
        public Failed {
            Require.nonNull(planned, "planned");
            Require.nonNull(error, "error");
            Require.that(error.severity() == Severity.ERROR, "error must have ERROR severity");
        }
    }

    /**
     * The item was not written because the plan said SKIP (error in the plan, or nothing to do).
     *
     * @param planned not null
     */
    record Skipped(PlannedWrite planned) implements WriteOutcome {
        public Skipped {
            Require.nonNull(planned, "planned");
        }
    }
}
