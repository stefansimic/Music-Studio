package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.nio.file.Path;

/**
 * Read-only access to the user's source folders: listing candidate files and fingerprinting them.
 * Implemented by music-studio-storage (local disk) and by in-memory fakes.
 *
 * <p><b>Side effects:</b> none. Never creates, modifies, renames or deletes anything; does not change access/modify
 * times beyond what the OS does for directory reads.
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent use.
 */
public interface MediaFileSource {

    /**
     * Lists the regular files under {@code root}.
     *
     * <p><b>Preconditions:</b> root not null (NPE) and absolute (IAE); options not null (NPE);
     * cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code files} contains every regular file directly in {@code root} and, if {@code options.recursive()},
     *       in all subdirectories – absolute, normalised, unique, each starting with {@code root}.</li>
     *   <li>Symbolic links to regular files are included (as the link path). Symbolic links to directories are
     *       <em>not</em> followed (no loops, no escaping the tree).</li>
     *   <li>If {@code !options.includeHidden()}: entries whose name starts with "." (and, on Windows, entries with the
     *       hidden attribute) are not listed and not descended into; each one is recorded once in {@code skipped}
     *       with reason HIDDEN (a hidden directory as one entry, its content is not enumerated).</li>
     *   <li>Symbolic links to directories are recorded in {@code skipped} with reason DIRECTORY_SYMLINK; excluded
     *       directories with reason EXCLUDED. Without {@code recursive}, subdirectories are neither listed nor
     *       recorded.</li>
     *   <li>Symbolic links whose target does not exist are recorded in {@code skipped} with reason
     *       BROKEN_SYMLINK (also without {@code recursive}). Other non-regular files (devices, pipes, sockets) are
     *       ignored.</li>
     *   <li>Directories in {@code options.excludedDirectories()} (and their content) are skipped.</li>
     *   <li>A subdirectory that cannot be read is reported once as {@code LISTING_ACCESS_DENIED} (WARNING) in
     *       {@code issues}; listing continues.</li>
     *   <li>The order of {@code files} is unspecified (callers sort with PathOrder).</li>
     * </ul>
     * <p><b>Cancellation:</b> checked at least once per directory; throws OperationCancelledException.
     *
     * @param root    absolute path of the directory to list; not null
     * @param options recursion/hidden/exclusion options; not null
     * @param cancel  cooperative cancellation; not null (use Cancellation.NONE)
     * @return listing, never null
     * @throws NullPointerException        if an argument is null
     * @throws IllegalArgumentException    if {@code root} is relative
     * @throws MediaAccessException        NOT_FOUND if root does not exist; NOT_A_DIRECTORY; ACCESS_DENIED if root
     *                                     itself is unreadable; IO for other failures of root
     * @throws OperationCancelledException if cancelled
     */
    DirectoryListing list(Path root, ListingOptions options, Cancellation cancel) throws MediaAccessException;

    /**
     * Returns size and last-modified time of a file, following symlinks.
     *
     * <p><b>Preconditions:</b> file not null (NPE), absolute (IAE).
     * <p><b>Postconditions:</b> sizeBytes equals the current file size; lastModified has the precision the
     * filesystem provides (callers compare fingerprints only from the same source).
     *
     * @param file absolute path of a regular file; not null
     * @return the fingerprint; never null
     * @throws NullPointerException     if {@code file} is null
     * @throws IllegalArgumentException if {@code file} is relative
     * @throws MediaAccessException     NOT_FOUND, ACCESS_DENIED, IO
     */
    FileFingerprint fingerprint(Path file) throws MediaAccessException;
}
