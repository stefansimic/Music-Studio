package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;

/**
 * Executes an {@link ExportPlan}: writes tagged <b>copies</b> into the output folder. The originals are never
 * modified, moved or deleted, and nothing existing is overwritten.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use for different output folders; blocks on I/O.
 */
public interface ApplyExportUseCase {

    /**
     * <p><b>Preconditions:</b> arguments not null (NPE); {@code plan.executable()} (IAE); no other apply to the same
     * output folder is running (ISE).
     * <p><b>Postconditions/Guarantees:</b> per WRITE file in plan order: the source is unchanged since the plan, the
     * copy is written to a temp file next to the target, re-read and verified, then moved without replacing; temp
     * files are always removed. Failures and cancellation are reported, never thrown. A full disk aborts the run.
     * Progress phase "write" with byte progress.
     */
    ExportReport apply(ExportPlan plan, ProgressListener progress, Cancellation cancel);
}
