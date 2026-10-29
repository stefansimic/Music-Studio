package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;

/**
 * {@link TagWriter} for MP3 files, implemented with jaudiotagger. The source is opened read-only; the target is the
 * only file opened for writing. The audio payload is copied byte for byte (never re-encoded) and the tags are edited
 * on the copy.
 *
 * <p><b>Thread-safety:</b> safe for concurrent calls. jaudiotagger keeps process-wide mutable state (its option
 * singleton), so every library call is serialised with the same lock the reader uses, and the options this class
 * changes are restored before the lock is released.
 */
public final class JaudiotaggerTagWriter implements TagWriter {

    private static final Set<MediaFormat> WRITABLE = Set.of(MediaFormat.MP3);
    private static final FormatCapabilities MP3 = new FormatCapabilities(MediaFormat.MP3,
            Set.of(TagVersion.ID3V1_0, TagVersion.ID3V1_1, TagVersion.ID3V2_2, TagVersion.ID3V2_3,
                    TagVersion.ID3V2_4),
            EnumSet.allOf(V2Encoding.class), EnumSet.allOf(Field.class), false);
    private static final int COPY_BUFFER = 1 << 20;

    /** Keeps the library logger (and its level) from being garbage collected. */
    private final Logger libraryLogger = JaudiotaggerLogging.quiet();

    @Override
    public Set<MediaFormat> writableFormats() {
        return WRITABLE;
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(WRITABLE.contains(format), "%s cannot be written", format);
        return MP3;
    }

    @Override
    public List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException {
        Require.absolute(source, "source");
        Require.nonNull(request, "request");
        requireWritable(request);
        requireReadableSource(source, request);
        try {
            return LibraryLock.run(() -> {
                MP3File mp3 = new MP3File(source.toFile(), MP3File.LOAD_ALL, true);
                List<Issue> issues = new ArrayList<>();
                var options = request.options();
                if (options.id3v2() != Id3v2Choice.NONE && mp3.hasID3v2Tag()) {
                    AbstractID3v2Tag existing = mp3.getID3v2Tag();
                    AbstractID3v2Tag converted = Id3Writing.convert(existing, options.id3v2());
                    List<String> dropped = Id3Writing.droppedFrames(existing, converted);
                    if (!dropped.isEmpty()) {
                        issues.add(Id3Writing.droppedIssue(dropped));
                    }
                }
                if (options.id3v2() == Id3v2Choice.NONE && mp3.hasID3v2Tag()) {
                    issues.add(familyIssue("ID3v2", options.removeOtherVersions()));
                }
                boolean v1Slot = options.id3v1() == Id3v1Choice.NONE && mp3.hasID3v1Tag();
                if (v1Slot) {
                    issues.add(familyIssue("ID3v1", options.removeOtherVersions()));
                }
                return issues;
            });
        } catch (Exception e) {
            throw LibraryExceptions.map(source, e);
        }
    }

    private static Issue familyIssue(String family, boolean remove) {
        return remove
                ? Issue.warning(IssueCode.OTHER_VERSION_REMOVED, "The existing " + family + " tag is removed")
                : Issue.warning(IssueCode.OTHER_VERSION_KEPT,
                        "The existing " + family + " tag is kept as it is and may disagree with the new tags");
    }

    @Override
    public void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel,
                          ByteProgress progress) throws MediaAccessException {
        Require.absolute(source, "source");
        Require.absolute(target, "target");
        Require.nonNull(request, "request");
        Require.nonNull(cancel, "cancel");
        Require.nonNull(progress, "progress");
        requireWritable(request);
        requireReadableSource(source, request);
        Require.that(!source.normalize().equals(target.normalize()), "target must not be the source");
        Require.that(!Files.exists(target), "target must not exist: %s", target);
        Require.that(target.getParent() != null && Files.isDirectory(target.getParent()),
                "the folder of the target must exist");
        assert libraryLogger != null;
        cancel.throwIfCancelled();
        copy(source, target, cancel, progress);
        try {
            LibraryLock.run(() -> {
                editTags(target, request);
                return null;
            });
            if (!java.util.Arrays.equals(Id3Writing.audioHash(source), Id3Writing.audioHash(target))) {
                throw new MediaAccessException(Reason.IO, target, "the audio data changed while writing the tags", null);
            }
        } catch (MediaAccessException | OperationCancelledException e) {
            throw e;
        } catch (Exception e) {
            throw LibraryExceptions.map(target, e);
        }
    }

    private static void requireWritable(TagWriteRequest request) {
        Require.that(WRITABLE.contains(request.format()), "%s cannot be written", request.format());
        Require.that(MP3.unsupported(request.options()).isEmpty(), "the options are not supported by this writer");
    }

    private static void requireReadableSource(Path source, TagWriteRequest request) throws MediaAccessException {
        Require.that(MediaFormat.ofFileName(source.getFileName().toString()).filter(f -> f == request.format())
                .isPresent(), "source is not a %s file: %s", request.format(), source);
        if (!Files.exists(source)) {
            throw new MediaAccessException(Reason.NOT_FOUND, source, "no such file", null);
        }
        Require.that(Files.isRegularFile(source), "source must be a regular file: %s", source);
        if (!Files.isReadable(source)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, source, "access denied", null);
        }
    }

    /** Copies the file in chunks, checking for cancellation after each chunk (1 MiB, well within 8 MiB). */
    private static void copy(Path source, Path target, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            long total = Files.size(source);
            long done = 0;
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                cancel.throwIfCancelled();
                out.write(buffer, 0, read);
                done += read;
                progress.onBytes(done, total);
            }
            progress.onBytes(total, total);
        } catch (IOException e) {
            throw mapCopy(source, target, e);
        }
    }

    private static MediaAccessException mapCopy(Path source, Path target, IOException e) {
        String reason = e instanceof FileSystemException fse && fse.getReason() != null ? fse.getReason() : "";
        String text = (reason + " " + (e.getMessage() == null ? "" : e.getMessage())).toLowerCase();
        if (text.contains("no space")) {
            return new MediaAccessException(Reason.NO_SPACE, target, "no space left on the device", e);
        }
        if (e instanceof java.nio.file.AccessDeniedException || text.contains("permission denied")) {
            return new MediaAccessException(Reason.ACCESS_DENIED, target, "access denied", e);
        }
        if (e instanceof java.nio.file.NoSuchFileException) {
            return new MediaAccessException(Reason.NOT_FOUND, source, "no such file", e);
        }
        return new MediaAccessException(Reason.IO, target, "cannot copy the file: "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
    }

    /** Edits the tags of the copy. Runs under the library lock. */
    private static void editTags(Path target, TagWriteRequest request) throws Exception {
        var options = request.options();
        TagOptionSingleton singleton = TagOptionSingleton.getInstance();
        boolean oldV1Save = singleton.isId3v1Save();
        boolean oldV2Save = singleton.isId3v2Save();
        try {
            singleton.setId3v1Save(false); // the ID3v1 block is written byte for byte below
            MP3File mp3 = new MP3File(target.toFile());
            if (options.id3v2() != Id3v2Choice.NONE) {
                AbstractID3v2Tag existing = mp3.hasID3v2Tag() ? mp3.getID3v2Tag() : null;
                AbstractID3v2Tag tag = Id3Writing.convert(existing, options.id3v2());
                Id3Writing.joinGenres(tag);
                Id3Writing.apply(tag, request.changes(), options.v2Encoding());
                mp3.setID3v2TagOnly(tag);
                singleton.setId3v2Save(true);
                mp3.save();
            } else if (options.removeOtherVersions() && mp3.hasID3v2Tag()) {
                try (RandomAccessFile file = new RandomAccessFile(target.toFile(), "rw")) {
                    Id3Writing.stripV2(file);
                }
            }
        } finally {
            singleton.setId3v1Save(oldV1Save);
            singleton.setId3v2Save(oldV2Save);
        }
        if (options.id3v1() != Id3v1Choice.NONE || options.removeOtherVersions()) {
            try (RandomAccessFile file = new RandomAccessFile(target.toFile(), "rw")) {
                Id3Writing.writeV1(file, request.id3v1().orElse(null), options.removeOtherVersions());
            }
        }
    }
}
