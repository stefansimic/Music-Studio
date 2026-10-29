package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Produces a tagged copy of a media file. There is deliberately no in-place write: the source is only ever read, the
 * result is written to a new path chosen by the caller (a temp file in the export folder). This shape is native to
 * jaudiotagger (copy, then write the copy) and to mp3agic ({@code Mp3File.save(newPath)}).
 *
 * <p><b>Thread-safety:</b> implementations must be safe to call from several threads; they may serialise internally
 * (jaudiotagger does, because of its global option singleton). Calls may block.
 */
public interface TagWriter {

    /** @return the formats this writer can write; not empty, unmodifiable, constant */
    Set<MediaFormat> writableFormats();

    /**
     * Declares what this writer can produce for a format.
     *
     * <p><b>Preconditions:</b> {@code format} is not null (NPE) and in {@link #writableFormats()} (IAE).
     * <p><b>Postconditions:</b> constant for the lifetime of the instance; never null.
     *
     * @param format the format; not null
     * @return the capabilities; never null
     */
    FormatCapabilities capabilities(MediaFormat format);

    /**
     * Dry-run of {@link #writeCopy}: reports, without writing anything, what the write would lose or change beyond
     * {@code request.changes()}: frames with no equivalent in the target version, tags removed because of
     * {@code removeOtherVersions}, tags of other families that stay.
     *
     * <p><b>Preconditions:</b> {@code source} is not null, absolute, a readable file of {@code request.format()};
     * {@code request} is not null; {@code capabilities(format).unsupported(options)} is empty (IAE).
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Every frame {@code writeCopy} will not carry over is listed as {@code FRAME_NOT_REPRESENTABLE} (WARNING);
     *       the list is complete: {@code writeCopy} drops nothing that was not reported here.</li>
     *   <li>{@code OTHER_VERSION_REMOVED} / {@code OTHER_VERSION_KEPT} for tag slots affected by the options.</li>
     *   <li>No file is created or modified.</li>
     * </ul>
     *
     * @param source  absolute path of the original; not null
     * @param request what would be written; not null
     * @return issues (WARNING or INFO only; fatal problems are exceptions); never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT, IO
     */
    List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException;

    /**
     * Writes a copy of {@code source} to {@code target} with the requested tag state.
     *
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>{@code source}: not null, absolute, an existing regular file of {@code request.format()} (NPE/IAE)</li>
     *   <li>{@code target}: not null, absolute; does not exist; its parent directory exists; not the same file as
     *       {@code source} (NPE/IAE)</li>
     *   <li>{@code request}: not null; options supported per capabilities (IAE)</li>
     *   <li>{@code cancel}, {@code progress}: not null (NPE)</li>
     * </ul>
     * <p><b>Postconditions/Guarantees (normal return):</b>
     * <ul>
     *   <li>{@code source} is byte-for-byte unchanged, including its last-modified time.</li>
     *   <li>{@code target} exists; its audio payload is identical to the source's.</li>
     *   <li>One slot per family: for every family with a selected version the target has exactly that version. An
     *       existing tag of the same family in another version is converted (frames mapped; frames without an
     *       equivalent dropped exactly as reported by {@link #preflight}). For a family set to NONE the source's tag
     *       is copied unchanged if {@code !removeOtherVersions}, and absent otherwise.</li>
     *   <li>ID3v2 slot: fields in {@code changes.set()} have the given values, fields in {@code changes.clear()} are
     *       absent, every other frame (unknown frames, TXXX incl. ReplayGain, PRIV, lyrics, pictures) is preserved.</li>
     *   <li>Text encoding as {@code options.v2Encoding()}; AUTO = Latin-1 if every character fits, otherwise UTF-16
     *       (v2.2, v2.3) or UTF-8 (v2.4).</li>
     *   <li>ID3v1 slot (if selected): exactly {@code request.id3v1()}, no further transformation.</li>
     *   <li>Multi-value genres are joined with "; " for v2.2/v2.3 and NUL-separated for v2.4.</li>
     *   <li>Before returning, {@code progress} has received at least one final call (total, total).</li>
     * </ul>
     * <p><b>On failure or cancellation:</b> {@code source} is unchanged; {@code target} may exist in an unspecified
     * state. The caller deletes it (it is a temp path).
     * <p><b>Cancellation:</b> checked before starting and at least every 8 MiB of copied payload.
     *
     * @param source   absolute path of the original; read only; not null
     * @param target   absolute path of the new file; must not exist; not null
     * @param request  what to write; not null
     * @param cancel   cooperative cancellation; not null
     * @param progress byte progress of this file; not null ({@link ByteProgress#NONE})
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT, NO_SPACE, FILE_TOO_LARGE, IO
     * @throws OperationCancelledException if cancelled
     */
    void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException;
}
