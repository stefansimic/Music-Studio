package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The output side of an export: collision checks, directories, temp files and the final no-replace move. Implemented
 * by music-studio-storage (local disk) and by a fake. Story 003 uses this subset; 004 adds space checks, backups
 * and undo deletions.
 *
 * <p><b>Safety:</b> no method can replace or delete a user file. {@link #moveNoReplace} never overwrites;
 * {@link #deleteTemp} only deletes names produced by {@link #reserveTempPath}.
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent use on different targets.
 */
public interface ExportTarget {

    /** Suffix of every temp file name. */
    String TEMP_SUFFIX = ".musicstudio-tmp";

    /**
     * Finds an existing entry that a file at {@code target} would collide with: the same name, also when it differs
     * only in case or Unicode normalisation (FAT32/exFAT/macOS treat those as equal).
     *
     * <p><b>Preconditions:</b> target not null, absolute (NPE/IAE). <b>Postconditions:</b> nothing is modified.
     *
     * @param target the planned file; not null
     * @return the colliding existing path, or empty (also when the parent does not exist); never null
     * @throws MediaAccessException ACCESS_DENIED, IO
     */
    Optional<Path> findCollision(Path target) throws MediaAccessException;

    /**
     * Creates a directory and its missing parents.
     *
     * @param directory absolute; not null
     * @return the directories that were created, outermost first; empty if it existed; never null
     * @throws MediaAccessException ALREADY_EXISTS if a file is in the way; ACCESS_DENIED; NO_SPACE; IO
     */
    List<Path> createDirectories(Path directory) throws MediaAccessException;

    /**
     * A temp path next to {@code finalTarget}: same directory, name {@code .<name>.<6 hex>.musicstudio-tmp}, at most
     * 255 characters, not existing. Nothing is created.
     *
     * @param finalTarget absolute; not null; its parent exists
     * @return the temp path; never null
     * @throws MediaAccessException IO
     */
    Path reserveTempPath(Path finalTarget) throws MediaAccessException;

    /**
     * Moves a temp file to its final name without ever replacing an existing file.
     *
     * <p><b>Preconditions:</b> both absolute, same directory, temp ends with {@link #TEMP_SUFFIX} (IAE).
     * <p><b>Postconditions:</b> the target has the temp's content and modification time; the temp is gone.
     * <p><b>On failure:</b> ALREADY_EXISTS if the target exists – the existing target is unchanged and the temp
     * still exists (the caller deletes it).
     *
     * @param temp   the written temp file; not null
     * @param target the final path; not null
     * @throws MediaAccessException ALREADY_EXISTS, NOT_FOUND (temp missing), ACCESS_DENIED, IO
     */
    void moveNoReplace(Path temp, Path target) throws MediaAccessException;

    /**
     * Deletes a temp file if it exists.
     *
     * @param temp absolute, name ends with {@link #TEMP_SUFFIX} (IAE otherwise); not null
     * @throws MediaAccessException ACCESS_DENIED, IO
     */
    void deleteTemp(Path temp) throws MediaAccessException;

    /**
     * Size and modification time of a file, following symlinks.
     *
     * @param file absolute; not null
     * @return the fingerprint; never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, IO
     */
    FileFingerprint fingerprint(Path file) throws MediaAccessException;
}
