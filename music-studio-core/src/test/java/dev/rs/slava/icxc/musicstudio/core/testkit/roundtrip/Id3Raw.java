package dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A tiny ID3 parser, independent of jaudiotagger, used to check on-disk results (version, frame encoding byte,
 * frame payloads, audio payload). Supports v2.2/2.3/2.4 without unsynchronisation or extended headers.
 */
public final class Id3Raw {

    private Id3Raw() {}

    /** One frame: id as written (3 chars in v2.2, 4 otherwise) and the raw body (without frame header). */
    public record Frame(String id, byte[] body) {
        /** @return the text encoding byte of a text frame (first body byte) */
        public int encodingByte() {
            return body[0] & 0xFF;
        }
    }

    /** A parsed ID3v2 tag. {@code totalSize} includes the 10 byte header and padding. */
    public record V2(int major, int totalSize, List<Frame> frames) {
        /** @return the first frame with this id, if any */
        public Optional<Frame> frame(String id) {
            return frames.stream().filter(f -> f.id().equals(id)).findFirst();
        }

        /** @return all frame ids in file order */
        public List<String> ids() {
            return frames.stream().map(Frame::id).toList();
        }
    }

    /** @return the leading ID3v2 tag, if present */
    public static Optional<V2> v2(byte[] file) {
        if (file.length < 10 || file[0] != 'I' || file[1] != 'D' || file[2] != '3') {
            return Optional.empty();
        }
        int major = file[3];
        int size = synchsafe(file, 6);
        List<Frame> frames = new ArrayList<>();
        int pos = 10;
        int end = 10 + size;
        int idLen = major == 2 ? 3 : 4;
        int headerLen = major == 2 ? 6 : 10;
        while (pos + headerLen <= end && file[pos] != 0) {
            String id = new String(file, pos, idLen, StandardCharsets.ISO_8859_1);
            int bodyLen = switch (major) {
                case 2 -> ((file[pos + 3] & 0xFF) << 16) | ((file[pos + 4] & 0xFF) << 8) | (file[pos + 5] & 0xFF);
                case 3 -> beInt(file, pos + 4);
                default -> synchsafe(file, pos + 4);
            };
            frames.add(new Frame(id, Arrays.copyOfRange(file, pos + headerLen, pos + headerLen + bodyLen)));
            pos += headerLen + bodyLen;
        }
        return Optional.of(new V2(major, end, frames));
    }

    /** @return the 128 byte ID3v1 block at the end of the file, if present */
    public static Optional<byte[]> v1(byte[] file) {
        if (file.length >= 128 && file[file.length - 128] == 'T' && file[file.length - 127] == 'A'
                && file[file.length - 126] == 'G') {
            return Optional.of(Arrays.copyOfRange(file, file.length - 128, file.length));
        }
        return Optional.empty();
    }

    /** @return the bytes between the ID3v2 tag (and its zero padding) and the ID3v1 block */
    public static byte[] audioPayload(byte[] file) {
        int start = v2(file).map(V2::totalSize).orElse(0);
        while (start < file.length && file[start] == 0) {
            start++;
        }
        int end = v1(file).isPresent() ? file.length - 128 : file.length;
        return Arrays.copyOfRange(file, start, end);
    }

    /** @return the text of an ID3v1 field at {@code offset} with {@code length} bytes, decoded with {@code charset} */
    public static String v1Text(byte[] block, int offset, int length, java.nio.charset.Charset charset) {
        int end = offset;
        while (end < offset + length && block[end] != 0) {
            end++;
        }
        return new String(block, offset, end - offset, charset);
    }

    /** @return {@code TAG} title/artist/album raw text with the given charset */
    public static String v1Title(byte[] block, java.nio.charset.Charset cs) {
        return v1Text(block, 3, 30, cs);
    }

    public static String v1Artist(byte[] block, java.nio.charset.Charset cs) {
        return v1Text(block, 33, 30, cs);
    }

    public static String v1Album(byte[] block, java.nio.charset.Charset cs) {
        return v1Text(block, 63, 30, cs);
    }

    private static int synchsafe(byte[] b, int at) {
        return ((b[at] & 0x7F) << 21) | ((b[at + 1] & 0x7F) << 14) | ((b[at + 2] & 0x7F) << 7) | (b[at + 3] & 0x7F);
    }

    private static int beInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
