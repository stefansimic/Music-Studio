package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;

/**
 * Dry-run of an export: decides target, tag changes and problems per file without writing anything.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; blocks on I/O (collision checks, preflight).
 */
public interface PlanExportUseCase {

    /**
     * <p><b>Preconditions:</b> request, progress, cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b> one {@link PlannedFile} per proposal in order; no file is created,
     * changed or deleted; progress phase "plan".
     *
     * @throws OperationCancelledException if cancelled
     */
    ExportPlan plan(PlanExportRequest request, ProgressListener progress, Cancellation cancel);
}
