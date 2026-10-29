package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import java.nio.file.Path;
import java.util.Set;

/**
 * Reads the tags and stream properties of media files. Implemented per library (jaudiotagger now, mp3agic possible)
 * and per format family (MP3; MP4 in 012).
 *
 * <p><b>Side effects:</b> none. Files are opened read-only and closed before return; content and last-modified time
 * stay byte-for-byte unchanged (verified by checksum in the contract test).
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent calls on different files.
 */
public interface TagReader {

    /**
     * @return the formats this reader can read; non-empty, unmodifiable, constant for the instance's lifetime
     */
    Set<MediaFormat> readableFormats();

    /**
     * Reads all tag layers, stream info and a description of the embedded cover of one file.
     *
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>file not null (NPE), absolute (IAE)</li>
     *   <li>{@code MediaFormat.ofFileName(file)} is present and contained in {@link #readableFormats()} (IAE)</li>
     * </ul>
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code result.format()} equals the file's format.</li>
     *   <li>One {@code TagLayer} per tag slot present in the file, with the exact version found
     *       (e.g. ID3v1.0 vs ID3v1.1 decided by the track byte; ID3v2.2 with 3-character frame IDs is supported).
     *       A file without tags yields an empty layer list, never an exception.</li>
     *   <li>Text values are decoded from their declared encoding (Latin-1, UTF-16 with/without BOM, UTF-16BE, UTF-8),
     *       stripped of padding, trailing NULs and surrounding whitespace; empty values are absent.</li>
     *   <li>Track/disc "3/12", "03", "3" become {@code Position}; ID3v1 track 0 is absent.</li>
     *   <li>Genre references "(17)", "17", "(17)Rock" and ID3v1 genre bytes are resolved to text via
     *       {@code Id3v1Genres}; 255 / unknown numbers are absent. Multiple v2.4 genres (NUL-separated) become
     *       several {@code Genre}s in order.</li>
     *   <li>A value that cannot be mapped to its field type (e.g. TRCK "A1") is omitted from the metadata and
     *       reported as {@code READ_VALUE_INVALID} (WARNING, with field and raw value in the message).</li>
     *   <li>A damaged tag in an otherwise readable file: the readable layers are returned, the damage is reported as
     *       {@code TAG_DAMAGED} (ERROR). Exception {@code CORRUPT} only when nothing usable can be read.</li>
     *   <li>{@code stream()} is present if the audio/video headers can be parsed.</li>
     *   <li>{@code cover()} describes the front cover (or the first picture, with type OTHER); bytes are not
     *       loaded.</li>
     *   <li>Fields the format has no slot for are simply absent.</li>
     * </ul>
     * <p><b>On failure:</b> the file is unchanged. Implementations must report every failure as a
     * {@link MediaAccessException}; they must not let library or runtime exceptions escape.
     *
     * @param file absolute path of the file to read; not null
     * @return the read result; never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT (not a parsable file of this format, incl.
     *         0-byte files), IO
     */
    TagReadResult read(Path file) throws MediaAccessException;
}
