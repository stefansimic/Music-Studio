package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagNotFoundException;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;

/**
 * Reads the ID3 tags of a file in which jaudiotagger finds no MPEG audio header (broken or missing audio, or an ID3v2
 * header claiming more bytes than the file has). {@code MP3File} gives up on such files although their tags may be
 * fine; the {@code TagReader} contract allows CORRUPT only when nothing usable can be read.
 *
 * <p>Called with {@code JaudiotaggerTagReader.LIBRARY_LOCK} held. Opens the file read-only.
 */
final class TagsWithoutAudio {

    private static final int V2_HEADER = 10;
    private static final int V1_SIZE = 128;
    private static final int V24_FOOTER_FLAG = 0x10;

    private TagsWithoutAudio() {
    }

    private record Found(Optional<AbstractID3v2Tag> v2, Optional<ID3v1Tag> v1, List<Issue> issues) {
    }

    static TagReadResult read(Path file, Exception noAudio) throws MediaAccessException {
        Found found = find(file);
        List<Issue> issues = new ArrayList<>(found.issues());
        issues.add(Issue.warning(IssueCode.NO_AUDIO_STREAM,
                "no MPEG audio found after the tags; the file will probably not play"));
        List<TagLayer> layers = new ArrayList<>();
        Optional<CoverInfo> cover = Optional.empty();
        if (found.v2().isPresent()) {
            layers.add(Id3Reading.v2Layer(found.v2().get(), issues));
            cover = Id3Reading.coverArtwork(found.v2().get()).map(Id3Reading::coverInfo);
        }
        found.v1().ifPresent(v1 -> layers.add(Id3Reading.v1Layer(v1, issues)));
        if (layers.isEmpty()) {
            throw LibraryExceptions.map(file, noAudio);
        }
        List<Issue> located = issues.stream().map(issue -> issue.forFile(file)).toList();
        return new TagReadResult(MediaFormat.MP3, layers, Optional.empty(), cover, located);
    }

    static Optional<CoverArt> readCover(Path file, Exception noAudio) throws MediaAccessException {
        Found found = find(file);
        if (found.v2().isEmpty() && found.v1().isEmpty()) {
            throw LibraryExceptions.map(file, noAudio);
        }
        return found.v2().flatMap(Id3Reading::coverArtwork).map(Id3Reading::coverArt);
    }

    private static Found find(Path file) throws MediaAccessException {
        List<Issue> issues = new ArrayList<>();
        String name = file.getFileName().toString();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            Optional<AbstractID3v2Tag> v2 = readV2(raf, name, issues);
            Optional<ID3v1Tag> v1 = readV1(raf, name);
            return new Found(v2, v1, issues);
        } catch (IOException e) {
            throw LibraryExceptions.map(file, e);
        }
    }

    private static Optional<AbstractID3v2Tag> readV2(RandomAccessFile raf, String name, List<Issue> issues)
            throws IOException {
        long length = raf.length();
        if (length < V2_HEADER) {
            return Optional.empty();
        }
        byte[] header = new byte[V2_HEADER];
        raf.seek(0);
        raf.readFully(header);
        if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return Optional.empty();
        }
        long size = V2_HEADER + syncSafe(header, 6) + ((header[5] & V24_FOOTER_FLAG) != 0 ? V2_HEADER : 0);
        if (size > length) {
            issues.add(Id3Reading.damaged("the ID3v2 tag claims " + size + " bytes but the file has only " + length
                    + "; only the available part was read"));
        }
        byte[] bytes = new byte[(int) Math.min(size, Math.min(length, Integer.MAX_VALUE))];
        raf.seek(0);
        raf.readFully(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {
            return Optional.ofNullable(switch (header[3]) {
                case 2 -> new ID3v22Tag(buffer, name);
                case 3 -> new ID3v23Tag(buffer, name);
                case 4 -> new ID3v24Tag(buffer, name);
                default -> null;
            });
        } catch (TagException | RuntimeException e) {
            issues.add(Id3Reading.damaged("the ID3v2 tag could not be read (" + e.getClass().getSimpleName() + ")"));
            return Optional.empty();
        }
    }

    private static Optional<ID3v1Tag> readV1(RandomAccessFile raf, String name) throws IOException {
        if (raf.length() < V1_SIZE) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ID3v11Tag(raf, name));
        } catch (TagNotFoundException notV11) {
            try {
                return Optional.of(new ID3v1Tag(raf, name));
            } catch (TagNotFoundException none) {
                return Optional.empty();
            }
        }
    }

    private static long syncSafe(byte[] bytes, int offset) {
        return ((bytes[offset] & 0x7FL) << 21) | ((bytes[offset + 1] & 0x7FL) << 14)
                | ((bytes[offset + 2] & 0x7FL) << 7) | (bytes[offset + 3] & 0x7FL);
    }
}
