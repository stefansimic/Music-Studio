package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;

/**
 * {@link TagWriter} for MP3 (ADR 0011): composes the copy itself – new ID3v2 bytes serialised by jaudiotagger, the
 * source payload copied byte for byte by our own cancellable loop, and an ID3v1 block encoded from core's
 * {@code Id3v1Values}. jaudiotagger never writes a file; the source is only opened for reading.
 *
 * <p><b>Thread-safety:</b> safe for concurrent calls; jaudiotagger is used under {@link LibraryLock} only while
 * tags are parsed, converted and serialised, not during the payload copy.
 */
public final class JaudiotaggerTagWriter implements TagWriter {

    private static final Set<MediaFormat> FORMATS = Set.of(MediaFormat.MP3);
    private static final FormatCapabilities CAPABILITIES = new FormatCapabilities(MediaFormat.MP3,
            EnumSet.of(TagVersion.ID3V1_0, TagVersion.ID3V1_1, TagVersion.ID3V2_2, TagVersion.ID3V2_3,
                    TagVersion.ID3V2_4),
            EnumSet.allOf(V2Encoding.class), EnumSet.allOf(Field.class), false);
    private static final int LOAD_TAGS = MP3File.LOAD_IDV1TAG | MP3File.LOAD_IDV2TAG;

    /** What will be written: the head (ID3v2) and tail (ID3v1) bytes, and the frames lost by conversion. */
    private record Plan(SourceLayout layout, byte[] head, byte[] tail, List<Issue> lost) {
    }

    /** Creates the writer and silences jaudiotagger's logging. */
    public JaudiotaggerTagWriter() {
        JaudiotaggerLogging.silence();
    }

    @Override
    public Set<MediaFormat> writableFormats() {
        return FORMATS;
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(FORMATS.contains(format), "not writable: %s", format);
        return CAPABILITIES;
    }

    @Override
    public List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException {
        checkRequest(source, request);
        return plan(source, request).lost();
    }

    @Override
    public void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel,
                          ByteProgress progress) throws MediaAccessException {
        checkRequest(source, request);
        Require.absolute(target, "target");
        Require.that(!target.equals(source), "target must differ from source");
        Require.that(target.getParent() != null && Files.isDirectory(target.getParent()),
                "the target's directory must exist: %s", target);
        Require.that(!Files.exists(target, LinkOption.NOFOLLOW_LINKS), "target exists: %s", target);
        Require.nonNull(cancel, "cancel");
        Require.nonNull(progress, "progress");
        cancel.throwIfCancelled();
        Plan plan = plan(source, request);
        try {
            PayloadCopy.compose(source, plan.layout().v2End(), plan.layout().v1Start(), target, plan.head(),
                    plan.tail(), cancel, progress);
        } catch (IOException e) {
            throw LibraryExceptions.mapWrite(source, target, e);
        }
    }

    private void checkRequest(Path source, TagWriteRequest request) {
        Require.absolute(source, "source");
        Require.nonNull(request, "request");
        Path name = source.getFileName();
        Optional<MediaFormat> format = name == null ? Optional.empty() : MediaFormat.ofFileName(name.toString());
        Require.that(format.isPresent() && FORMATS.contains(format.get()) && format.get() == request.format(),
                "not a writable format or the request does not fit: %s", source);
        Require.that(CAPABILITIES.unsupported(request.options()).isEmpty(), "unsupported options: %s",
                request.options());
    }

    private static Plan plan(Path source, TagWriteRequest request) throws MediaAccessException {
        checkSource(source);
        SourceLayout layout;
        try {
            layout = SourceLayout.of(source);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.CORRUPT, source, "the ID3v2 tag is damaged (" + e.getMessage()
                    + ")", e);
        }
        LibraryLock.LOCK.lock();
        try {
            MP3File mp3;
            try {
                mp3 = new MP3File(source.toFile(), LOAD_TAGS, true);
            } catch (Exception e) {
                throw LibraryExceptions.map(source, e);
            }
            AbstractID3v2Tag existing = mp3.hasID3v2Tag() ? mp3.getID3v2Tag() : null;
            byte[] head;
            List<Issue> lost = List.of();
            Id3v2Choice choice = request.options().id3v2();
            if (choice == Id3v2Choice.NONE) {
                head = request.options().removeOtherVersions() || !layout.hasV2()
                        ? new byte[0] : readRange(source, 0, layout.v2End());
            } else {
                if (existing != null && existing.getInvalidFrames() > 0) {
                    throw new MediaAccessException(Reason.CORRUPT, source, "the ID3v2 tag is damaged ("
                            + existing.getInvalidFrames() + " unreadable frames); it is not rewritten", null);
                }
                int major = switch (choice) {
                    case V2_2 -> 2;
                    case V2_4 -> 4;
                    default -> 3;
                };
                V2Conversion.Result converted = existing == null
                        ? new V2Conversion.Result(V2Conversion.empty(major), List.of())
                        : V2Conversion.convert(existing, major);
                lost = converted.lost();
                V2Fields.apply(converted.tag(), major, request.changes(), request.options().v2Encoding());
                head = serialise(converted.tag());
            }
            byte[] tail;
            if (request.id3v1().isPresent()) {
                tail = Id3v1Block.encode(request.id3v1().get());
            } else {
                tail = request.options().removeOtherVersions() || !layout.hasV1()
                        ? new byte[0] : readRange(source, layout.v1Start(), layout.length());
            }
            return new Plan(layout, head, tail, lost);
        } catch (RuntimeException e) {
            throw LibraryExceptions.map(source, e);
        } finally {
            LibraryLock.LOCK.unlock();
        }
    }

    private static byte[] serialise(AbstractID3v2Tag tag) throws MediaAccessException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tag.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, Path.of("/").toAbsolutePath(),
                    "the ID3v2 tag cannot be serialised: " + e.getMessage(), e);
        }
    }

    private static byte[] readRange(Path file, long from, long to) throws MediaAccessException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] bytes = new byte[(int) (to - from)];
            raf.seek(from);
            raf.readFully(bytes);
            return bytes;
        } catch (IOException e) {
            throw LibraryExceptions.map(file, e);
        }
    }

    private static void checkSource(Path source) throws MediaAccessException {
        if (!Files.exists(source)) {
            throw new MediaAccessException(Reason.NOT_FOUND, source, "file not found", null);
        }
        if (!Files.isRegularFile(source)) {
            throw new MediaAccessException(Reason.IO, source, "not a regular file", null);
        }
        if (!Files.isReadable(source)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, source, "access denied", null);
        }
    }
}
