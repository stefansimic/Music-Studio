package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.util.function.Consumer;

/**
 * Lists the media files under a folder and reads their tags, streaming one result per file in path order.
 * Read-only: no file is created or modified.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; call it from a worker thread (it blocks on I/O).
 */
public interface ScanMediaUseCase {

    /**
     * Scans a folder.
     *
     * <p><b>Preconditions:</b> request, sink, progress, cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code sink} receives exactly one {@link ScanItem} per file whose format is in {@code request.formats()},
     *       in {@code PathOrder} order, on the calling thread, before scan returns. Files with tags, without tags
     *       (empty layers) and unreadable files ({@link UnreadableFile} with READ_FAILED) are all delivered –
     *       none is skipped.</li>
     *   <li>Memory: results are not retained; only the path list is held (streaming, 10 000+ files).</li>
     *   <li>Files with other extensions are not read; they are counted in {@code ignoredFiles} and
     *       {@code ignoredByExtension}.</li>
     *   <li>Hidden files/directories (unless includeHidden), directory symlinks, broken symlinks and excluded
     *       directories are listed in {@code skipped}, sorted by PathOrder, so the user sees what was not
     *       scanned.</li>
     *   <li>{@link ScannedFile#conflicts()} = {@code tags.conflicts(request.v1Comparison())}.</li>
     *   <li>{@code status} = COMPLETED, CANCELLED (sink received a prefix of the order) or FAILED (root
     *       missing/unreadable; issue ROOT_NOT_FOUND (also: not a folder) / LISTING_ACCESS_DENIED / READ_FAILED
     *       (other I/O failure of the root); sink received nothing).</li>
     *   <li>Counters: found == read + failed (if COMPLETED); filesWithErrors = read files with an ERROR issue.</li>
     *   <li>A reader failure of any kind (also an unexpected runtime exception of an adapter) becomes an
     *       {@link UnreadableFile}; one bad file never aborts the scan.</li>
     *   <li>{@code progress} receives phase "list" then "read" events with done/total files.</li>
     *   <li>No file or directory is created, modified or deleted.</li>
     * </ul>
     * <p><b>On sink failure:</b> an exception thrown by sink aborts the scan and propagates unchanged.
     *
     * @param request  what to scan; not null
     * @param sink     receives results in order; not null; must not block for long
     * @param progress progress callback; not null (ProgressListener.NONE)
     * @param cancel   cooperative cancellation, checked between files; not null
     * @return summary; never null
     * @throws NullPointerException if an argument is null
     */
    ScanSummary scan(ScanRequest request, Consumer<? super ScanItem> sink, ProgressListener progress,
                     Cancellation cancel);
}
