package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;

/**
 * {@link TagReader} for MP3 files (ID3v1.0/1.1, ID3v2.2/2.3/2.4) implemented with jaudiotagger.
 *
 * <p>Files are opened with jaudiotagger's read-only mode; Lyrics3 is not loaded. Every library exception is mapped
 * to {@link MediaAccessException}. The contract is the {@link TagReader} Javadoc.
 *
 * <p><b>Thread-safety:</b> safe for concurrent calls. jaudiotagger's MP3 header parser keeps static state (concurrent
 * reads return wrong durations – found by the contract test), so all library access is serialised by
 * {@link #LIBRARY_LOCK}.
 */
public final class JaudiotaggerTagReader implements TagReader {

    private static final Set<MediaFormat> FORMATS = Set.of(MediaFormat.MP3);
    private static final int LOAD_TAGS = MP3File.LOAD_IDV1TAG | MP3File.LOAD_IDV2TAG;
    /** Guards every jaudiotagger call: the library is not thread-safe. Shared with the writer. */
    static final ReentrantLock LIBRARY_LOCK = LibraryLock.LOCK;

    /** Creates the reader and silences jaudiotagger's logging. */
    public JaudiotaggerTagReader() {
        JaudiotaggerLogging.silence();
    }

    @Override
    public Set<MediaFormat> readableFormats() {
        return FORMATS;
    }

    @Override
    public TagReadResult read(Path file) throws MediaAccessException {
        checkPreconditions(file);
        LIBRARY_LOCK.lock();
        try {
            return readLocked(file);
        } catch (RuntimeException e) {
            // also the tags-only fallback path: nothing but MediaAccessException may leave a port
            throw LibraryExceptions.map(file, e);
        } finally {
            LIBRARY_LOCK.unlock();
        }
    }

    private TagReadResult readLocked(Path file) throws MediaAccessException {
        checkFile(file);
        MP3File mp3;
        try {
            mp3 = new MP3File(file.toFile(), LOAD_TAGS, true);
        } catch (InvalidAudioFrameException e) {
            // no audio header: the tags may still be fine (CORRUPT only if nothing usable can be read)
            return TagsWithoutAudio.read(file, e);
        } catch (Exception e) {
            throw LibraryExceptions.map(file, e);
        }
        try {
            List<Issue> issues = new ArrayList<>();
            List<TagLayer> layers = new ArrayList<>();
            Optional<CoverInfo> cover = Optional.empty();
            AbstractID3v2Tag v2 = mp3.hasID3v2Tag() ? mp3.getID3v2Tag() : null;
            if (v2 != null) {
                layers.add(Id3Reading.v2Layer(v2, issues));
                cover = Id3Reading.coverArtwork(v2).map(Id3Reading::coverInfo);
            }
            ID3v1Tag v1 = mp3.hasID3v1Tag() ? mp3.getID3v1Tag() : null;
            if (v1 != null) {
                layers.add(Id3Reading.v1Layer(v1, issues));
            }
            List<Issue> located = issues.stream().map(issue -> issue.forFile(file)).toList();
            return new TagReadResult(MediaFormat.MP3, layers, Id3Reading.stream(mp3.getMP3AudioHeader()), cover,
                    located);
        } catch (RuntimeException e) {
            throw LibraryExceptions.map(file, e);
        }
    }

    @Override
    public Optional<CoverArt> readCover(Path file) throws MediaAccessException {
        checkPreconditions(file);
        LIBRARY_LOCK.lock();
        try {
            checkFile(file);
            MP3File mp3;
            try {
                mp3 = new MP3File(file.toFile(), LOAD_TAGS, true);
            } catch (InvalidAudioFrameException e) {
                return TagsWithoutAudio.readCover(file, e);
            } catch (Exception e) {
                throw LibraryExceptions.map(file, e);
            }
            return mp3.hasID3v2Tag()
                    ? Id3Reading.coverArtwork(mp3.getID3v2Tag()).map(Id3Reading::coverArt)
                    : Optional.empty();
        } catch (RuntimeException e) {
            throw LibraryExceptions.map(file, e);
        } finally {
            LIBRARY_LOCK.unlock();
        }
    }

    private void checkPreconditions(Path file) {
        Require.absolute(file, "file");
        Path name = file.getFileName();
        Optional<MediaFormat> format = name == null ? Optional.empty() : MediaFormat.ofFileName(name.toString());
        Require.that(format.isPresent() && FORMATS.contains(format.get()), "not a readable format: %s", file);
    }

    private static void checkFile(Path file) throws MediaAccessException {
        if (!Files.exists(file)) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "file not found", null);
        }
        if (!Files.isRegularFile(file)) {
            throw new MediaAccessException(Reason.IO, file, "not a regular file", null);
        }
        if (!Files.isReadable(file)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied", null);
        }
    }
}
