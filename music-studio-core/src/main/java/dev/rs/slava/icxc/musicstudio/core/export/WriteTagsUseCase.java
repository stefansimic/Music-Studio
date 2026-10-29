package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;

/**
 * Writes tagged copies of media files: plan (dry-run) first, then apply. The originals are never modified; every
 * new file is written to a temp name, verified by reading it back, and only then moved into place without replacing
 * anything. Story 004 wraps this with path naming, collision policy and undo.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; both calls block on I/O, so call them from a worker thread.
 */
public interface WriteTagsUseCase {

    /**
     * Dry-run. Examines every item and reports what apply would do.
     *
     * <p><b>Preconditions:</b> both arguments not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>Nothing is created, modified or deleted. Source files are only read; the only call to the write side is
     *       {@code ExportTarget.exists}.</li>
     *   <li>If the options need a version or encoding the writer cannot write, the plan has an
     *       {@code UNSUPPORTED_WRITE_VERSION} error, no item is examined and {@code executable()} is false.</li>
     *   <li>Otherwise one {@link PlannedWrite} per item in request order. An item is SKIP with an ERROR for: not a
     *       writable format, target equal to the source ({@code TARGET_IS_SOURCE}) or existing
     *       ({@code TARGET_EXISTS}, also when two items share a target), unreadable source ({@code READ_FAILED}),
     *       a value Latin-1 cannot hold ({@code V2_NOT_ENCODABLE}). It is SKIP with an INFO ({@code NOTHING_TO_DO})
     *       when the file already is as wanted.</li>
     *   <li>{@code changes} lists field, old value and new value; ID3v1 warnings (transliterated, truncated, dropped,
     *       skipped for non-Latin text, not representable) and the writer's preflight issues are in {@code issues}.
     *       The plan is deterministic: the same inputs give an equal plan.</li>
     * </ul>
     *
     * @param request what to write; not null
     * @param cancel  cooperative cancellation; not null. If cancelled, the remaining items are not planned.
     * @return the plan; never null
     */
    WriteTagsPlan plan(WriteTagsRequest request, Cancellation cancel);

    /**
     * Writes the planned files.
     *
     * <p><b>Preconditions:</b> all arguments not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>A plan that is not executable yields status REJECTED; nothing is written.</li>
     *   <li>SKIP items are reported as skipped. For each WRITE item: the source must still have the planned
     *       fingerprint ({@code SOURCE_CHANGED} otherwise, nothing written); the file is written to a temp path next
     *       to the target, read back and compared with the plan ({@code VERIFY_MISMATCH}), then moved to the target
     *       without replacing ({@code TARGET_EXISTS}). On any failure no target and no temp file remain.</li>
     *   <li>One failed item does not stop the run. A cancel stops before the next item and deletes the current temp.</li>
     *   <li>{@code progress} receives one event per handled item (phase "write").</li>
     *   <li>Original files are never opened for writing.</li>
     * </ul>
     *
     * @param plan     a plan from {@link #plan}; not null
     * @param progress progress listener; not null
     * @param cancel   cooperative cancellation; not null
     * @return the report; never null
     */
    WriteTagsReport apply(WriteTagsPlan plan, ProgressListener progress, Cancellation cancel);
}
