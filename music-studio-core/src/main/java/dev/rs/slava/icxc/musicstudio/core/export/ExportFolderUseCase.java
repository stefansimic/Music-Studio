package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;

/**
 * The whole export flow for a folder, shared by every frontend: validate options, scan, propose, plan and (when
 * requested) apply. Originals are never changed; see {@link ApplyExportUseCase}.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; blocks on I/O.
 */
public interface ExportFolderUseCase {

    /**
     * <p><b>Preconditions:</b> arguments not null (NPE).
     * <p><b>Postconditions/Guarantees:</b> options the writer cannot fulfil stop the flow before anything is
     * scanned. A failed or cancelled scan stops it before planning. The plan is always returned when the scan
     * completed. The export is applied only if {@code request.apply()} and the plan is executable. Without apply
     * nothing is written. Cancellation is reported through the scan summary or report status, not thrown.
     */
    ExportFolderResult export(ExportFolderRequest request, ProgressListener progress, Cancellation cancel);
}
