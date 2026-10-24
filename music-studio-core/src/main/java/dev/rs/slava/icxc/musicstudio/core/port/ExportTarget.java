package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import java.nio.file.Path;
import java.util.regex.Pattern;
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

    /** Marker inside every temp file name; the final extension follows it so a {@code TagReader} can read the temp. */
    String TEMP_MARKER = ".musicstudio-tmp";

    /** Shape of a temp name, see {@link #tempName}. */
    Pattern TEMP_NAME = Pattern.compile("\\..+\\.[0-9a-f]{6}\\.musicstudio-tmp(\\..+)?");

    /**
     * The temp name for a final file name: {@code .<stem>.<hex>.musicstudio-tmp[.<ext>]}, at most 255 UTF-8 bytes.
     * Pure; shared by all implementations so they name temps alike.
     *
     * @param finalName the final file name; not null, not blank
     * @param hex       a unique token, e.g. 6 hex digits; not null, not blank
     * @return the temp file name; never null
     */
    static String tempName(String finalName, String hex) {
        Require.nonBlank(finalName, "finalName");
        Require.nonBlank(hex, "hex");
        int dot = finalName.lastIndexOf('.');
        String stem = dot > 0 ? finalName.substring(0, dot) : finalName;
        String suffix = "." + hex + TEMP_MARKER + (dot > 0 ? finalName.substring(dot) : "");
        String head = "." + stem;
        int room = 255 - suffix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        while (head.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > room) {
            head = head.substring(0, head.offsetByCodePoints(head.length(), -1));
        }
        return head + suffix;
    }

    /**
     * @param fileName a file name; not null
     * @return true if the name was produced by {@link #tempName}
     */
    static boolean isTempName(String fileName) {
        Require.nonNull(fileName, "fileName");
        return TEMP_NAME.matcher(fileName).matches();
    }

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
     * A temp path next to {@code finalTarget}: same directory, name per {@link #tempName} (keeps the extension), at most
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
     * <p><b>Preconditions:</b> both absolute, same directory, temp is a {@link #isTempName temp name} (IAE).
     * <p><b>Postconditions:</b> the target has the temp's content and modification time; the temp is gone.
     * <p><b>On failure:</b> ALREADY_EXISTS if the target exists – the existing target is unchanged and the temp
     * still exists (the caller deletes it).
     *
     * @param temp   the written temp file; not null
     * @param target the final path; not null
     * <p><b>Known gap:</b> on file systems without hard links (FAT/exFAT) the move is check-then-rename, so a file
     * created by another process in that instant could be replaced.
     *
     * @throws MediaAccessException ALREADY_EXISTS, NOT_FOUND (temp missing), ACCESS_DENIED, IO
     */
    void moveNoReplace(Path temp, Path target) throws MediaAccessException;

    /**
     * Deletes a temp file if it exists.
     *
     * @param temp absolute, name is a {@link #isTempName temp name} (IAE otherwise); not null
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
