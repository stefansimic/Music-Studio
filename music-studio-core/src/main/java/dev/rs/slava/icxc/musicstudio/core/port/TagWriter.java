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
 * Writes tags into a <em>copy</em> of a media file. There is no in-place write (ADR 0004): the source is only read.
 * Implemented per library (jaudiotagger now; mp3agic possible) and per format family.
 *
 * <p>The result of a write is defined by core ({@code WriteExpectation.layersAfter}, ADR 0011): per tag family a
 * selected version is written with {@code changes} applied to the source's tag of that family (converted from
 * another version if needed); a family set to NONE keeps its source tag byte-for-byte unless
 * {@code removeOtherVersions}; the ID3v1 tag is exactly {@code request.id3v1()}. Everything else – unknown frames,
 * pictures, ReplayGain, lyrics, other comments – is preserved, except frames that cannot exist in the target
 * version, which {@link #preflight} lists before anything is written.
 *
 * <p><b>Side effects:</b> {@link #preflight} none; {@link #writeCopy} creates exactly one file, {@code target}.
 * The source's content and modification time never change.
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent calls on different files.
 */
public interface TagWriter {

    /** @return the formats this writer can write; non-empty, unmodifiable, constant */
    Set<MediaFormat> writableFormats();

    /**
     * @param format a writable format; not null
     * @return what can be written for it; never null; constant
     * @throws NullPointerException     if {@code format} is null
     * @throws IllegalArgumentException if {@code format} is not writable
     */
    FormatCapabilities capabilities(MediaFormat format);

    /**
     * Reports what a {@link #writeCopy} with this request would lose, without writing anything.
     *
     * <p><b>Preconditions:</b> source not null, absolute, of a writable format (NPE/IAE); request not null, its
     * format equals the source's and its options are supported by {@link #capabilities} (IAE).
     * <p><b>Postconditions:</b> one WARNING FRAME_NOT_REPRESENTABLE per source frame that has no equivalent in the
     * target version (e.g. PRIV in ID3v2.2); nothing is created or modified.
     *
     * @param source  the source file; not null
     * @param request the write request; not null
     * @return the issues; empty = nothing is lost; never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT (also: a damaged tag that would have to be
     *                              rewritten), IO
     */
    List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException;

    /**
     * Writes a copy of {@code source} with the requested tags to {@code target}.
     *
     * <p><b>Preconditions:</b> as {@link #preflight}; target not null, absolute, different from source, its parent
     * directory exists and the target itself does not exist (IAE); cancel and progress not null (NPE).
     * <p><b>Postconditions/Guarantees:</b> {@code target} holds the source's audio payload byte for byte and the
     * tags described in the class comment; nothing reported by preflight is dropped silently beyond that list; no
     * other file is created; the source is unchanged. Progress ends with {@code (total, total)}.
     * <p><b>On failure / cancellation:</b> the target may exist partially written – the caller deletes it (apply
     * always writes to a temp path). The source is unchanged.
     * <p><b>Cancellation:</b> checked at least every 8 MiB of copied data.
     *
     * @param source   the source file; not null
     * @param target   the file to create; not null
     * @param request  the write request; not null
     * @param cancel   cooperative cancellation; not null
     * @param progress byte progress of the copy; not null
     * @throws MediaAccessException        NOT_FOUND, ACCESS_DENIED, CORRUPT, NO_SPACE, IO
     * @throws OperationCancelledException if cancelled
     */
    void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException;
}
