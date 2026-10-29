package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;

/**
 * {@link TagReader} for MP3 files, implemented with jaudiotagger. Read-only: jaudiotagger opens the file read-only
 * and this class never calls a write method, nor does it change a global option of the library.
 *
 * <p><b>Thread-safety:</b> safe for concurrent calls. jaudiotagger itself is not (it has process-wide mutable
 * state), so reads are serialised with a lock.
 */
public final class JaudiotaggerTagReader implements TagReader {

    private static final Set<MediaFormat> READABLE = Set.of(MediaFormat.MP3);

    /** Keeps the library logger (and its level) from being garbage collected. */
    private final Logger libraryLogger = JaudiotaggerLogging.quiet();

    @Override
    public Set<MediaFormat> readableFormats() {
        return READABLE;
    }

    @Override
    public TagReadResult read(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        Require.that(MediaFormat.ofFileName(file.getFileName().toString()).filter(READABLE::contains).isPresent(),
                "unsupported file type: %s", file);
        assert libraryLogger != null;

        if (!Files.exists(file)) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "no such file", null);
        }
        if (!Files.isRegularFile(file)) {
            throw new MediaAccessException(Reason.UNSUPPORTED_FORMAT, file, "not a regular file", null);
        }
        if (!Files.isReadable(file)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied", null);
        }
        try {
            return LibraryLock.run(() -> {
                AudioFile audioFile = AudioFileIO.read(file.toFile());
                if (!(audioFile instanceof MP3File mp3)) {
                    throw new MediaAccessException(Reason.UNSUPPORTED_FORMAT, file, "not an MP3 file", null);
                }
                return Id3Reading.read(mp3, file);
            });
        } catch (MediaAccessException e) {
            throw e;
        } catch (Exception e) {
            throw LibraryExceptions.map(file, e);
        }
    }
}
