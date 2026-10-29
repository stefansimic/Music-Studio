package dev.rs.slava.icxc.musicstudio.core.port;

import java.nio.file.Path;

/**
 * The write side of the local filesystem, restricted to what a safe tag write needs. It cannot modify or delete
 * arbitrary files: it creates new temp names, moves a finished temp file to a name that does not exist yet, and deletes
 * only its own temp files. Story 004 completes this port (directories, store info, backups, undo).
 *
 * <p><b>Thread-safety:</b> safe for concurrent use on different paths.
 */
public interface ExportTarget {

    /**
     * Marker in the name of every temp file this port creates. The name keeps the extension of the target after the
     * marker (".Song.mp3" gets ".Song.4f9a1c.musicstudio-tmp.mp3"), so a reader can still recognise the format.
     */
    String TEMP_MARKER = ".musicstudio-tmp";

    /**
     * @param path absolute path; not null (NPE), absolute (IAE)
     * @return true if a file or directory exists at {@code path}; false if not, or if that cannot be determined;
     *         never throws for I/O problems. Never modifies anything.
     */
    boolean exists(Path path);

    /**
     * Reserves a name for a temp file next to {@code target}: a hidden name containing {@link #TEMP_MARKER} (followed by the
     * extension of {@code target}) in the same directory (so the final move is a rename on one filesystem). The file is not created.
     *
     * <p><b>Preconditions:</b> {@code target} is not null (NPE), absolute (IAE).
     * <p><b>Postconditions:</b> the result is in {@code target}'s directory, does not exist, contains the marker and
     * differs from every other result of this instance.
     *
     * @param target the final path of the file; not null
     * @return the temp path; never null
     */
    Path reserveTempPath(Path target);

    /**
     * Moves {@code temp} to {@code target} without ever replacing an existing file (atomic where the filesystem
     * allows: hard link plus unlink; otherwise check-then-rename, which leaves a tiny race on FAT sticks).
     *
     * <p><b>Preconditions:</b> both not null, absolute; {@code temp}'s name contains {@link #TEMP_MARKER} (IAE).
     * <p><b>Postconditions:</b> on normal return {@code target} has the content of {@code temp} and {@code temp} is
     * gone. On failure {@code target}, if it existed before, is unchanged.
     *
     * @param temp   the finished temp file; not null
     * @param target the final path; must not exist; not null
     * @throws MediaAccessException ALREADY_EXISTS (target exists), NOT_FOUND (temp is missing or not a regular file), ACCESS_DENIED, NO_SPACE, IO
     */
    void moveNoReplace(Path temp, Path target) throws MediaAccessException;

    /**
     * Deletes a temp file; does nothing if it does not exist.
     *
     * <p><b>Preconditions:</b> {@code temp} is not null, absolute, its name contains {@link #TEMP_MARKER} (IAE): no
     * other file can ever be deleted through this port.
     *
     * @param temp the temp path; not null
     * @throws MediaAccessException ACCESS_DENIED, IO
     */
    void deleteTemp(Path temp) throws MediaAccessException;
}
