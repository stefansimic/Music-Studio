package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * A folder tree that a {@link MediaFileSourceContract} builds before listing it. The real-disk implementation
 * ({@link LocalDiskTree}) writes into a {@code @TempDir}; the in-memory fake keeps the same tree in memory.
 * Relative names use "/" as separator.
 */
public interface TestTree {

    /** @return the absolute root directory of the tree */
    Path root();

    /** Creates a regular file with {@code sizeBytes} bytes (and its parent directories). @return its absolute path */
    Path file(String relative, int sizeBytes);

    /** Creates a directory (and its parents). @return its absolute path */
    Path directory(String relative);

    /** Creates a symbolic link to a file of this tree. @return the link path, empty if links are unsupported */
    Optional<Path> symlinkToFile(String relative, String targetRelative);

    /** Creates a symbolic link to a directory of this tree. @return the link path, empty if unsupported */
    Optional<Path> symlinkToDirectory(String relative, String targetRelative);

    /** Makes a directory unreadable. @return false if that is impossible here (Windows, running as root) */
    boolean denyAccess(String directoryRelative);

    /** Undoes {@link #denyAccess} so the temp directory can be deleted. */
    void restoreAccess(String directoryRelative);

    /** @return size and last-modified time of {@code file} as the tree knows them, independent of the subject */
    FileFingerprint expectedFingerprint(Path file);

    /** @return a description of every entry (name, kind, size, mtime), to detect any change of the tree */
    Map<String, String> snapshot();
}
