package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Parses the ID3 structure of an MP3 file straight from the specification – independent of every tagging library,
 * so writer contracts can check bytes without trusting the library under test.
 *
 * <p>Handles ID3v2.2/2.3/2.4 frame headers, v2.3 whole-tag unsynchronisation, v2.4 per-frame unsynchronisation and
 * data-length indicator, extended headers, the v2.4 footer, and a trailing 128-byte ID3v1 block. The payload is
 * everything between the end of the leading ID3v2 tag and the trailing ID3v1 block.
 */
public final class Id3Inspector {

    private Id3Inspector() {
    }

    /** One frame: id, flags (2 bytes, 0 for v2.2) and the decoded body (unsynchronisation and DLI removed). */
    public record Frame(String id, int flags, byte[] body) {
    }

    /** A parsed ID3v2 tag. */
    public record V2Tag(int major, int flags, List<Frame> frames, byte[] raw) {

        /** @return the frames with this id */
        public List<Frame> frames(String id) {
            return frames.stream().filter(f -> f.id().equals(id)).toList();
        }

        /** @return the first frame with this id */
        public Optional<Frame> frame(String id) {
            return frames.stream().filter(f -> f.id().equals(id)).findFirst();
        }

        /** @return the frame ids in order */
        public List<String> ids() {
            return frames.stream().map(Frame::id).toList();
        }
    }

    /** A parsed file. */
    public record Id3File(Optional<V2Tag> v2, byte[] payload, Optional<byte[]> v1Block) {
    }

    /** Parses a file. */
    public static Id3File inspect(Path file) {
        try {
            return inspect(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses file bytes. */
    public static Id3File inspect(byte[] bytes) {
        int end = bytes.length;
        Optional<byte[]> v1 = Optional.empty();
        if (bytes.length >= 128 && bytes[end - 128] == 'T' && bytes[end - 127] == 'A' && bytes[end - 126] == 'G') {
            v1 = Optional.of(Arrays.copyOfRange(bytes, end - 128, end));
            end -= 128;
        }
        Optional<V2Tag> v2 = Optional.empty();
        int start = 0;
        if (bytes.length >= 10 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            int major = bytes[3];
            int flags = bytes[5] & 0xFF;
            int size = syncsafe(bytes, 6);
            int total = 10 + size + (major == 4 && (flags & 0x10) != 0 ? 10 : 0);
            byte[] raw = Arrays.copyOfRange(bytes, 0, Math.min(total, bytes.length));
            byte[] body = Arrays.copyOfRange(bytes, 10, Math.min(10 + size, bytes.length));
            if (major <= 3 && (flags & 0x80) != 0) {
                body = deunsynchronise(body);
            }
            v2 = Optional.of(new V2Tag(major, flags, frames(major, flags, body), raw));
            start = Math.min(total, end);
        }
        return new Id3File(v2, Arrays.copyOfRange(bytes, start, Math.max(start, end)), v1);
    }

    private static List<Frame> frames(int major, int flags, byte[] body) {
        List<Frame> frames = new ArrayList<>();
        int pos = 0;
        if ((flags & 0x40) != 0 && major >= 3) { // extended header
            pos = major == 3 ? 4 + int32(body, 0) : syncsafe(body, 0);
        }
        int headerSize = major == 2 ? 6 : 10;
        int idLength = major == 2 ? 3 : 4;
        while (pos + headerSize <= body.length && body[pos] != 0) {
            String id = new String(body, pos, idLength, StandardCharsets.ISO_8859_1);
            int size;
            int frameFlags = 0;
            if (major == 2) {
                size = ((body[pos + 3] & 0xFF) << 16) | ((body[pos + 4] & 0xFF) << 8) | (body[pos + 5] & 0xFF);
            } else {
                size = major == 4 ? syncsafe(body, pos + 4) : int32(body, pos + 4);
                frameFlags = ((body[pos + 8] & 0xFF) << 8) | (body[pos + 9] & 0xFF);
            }
            int dataStart = pos + headerSize;
            if (size < 0 || dataStart + size > body.length) {
                break;
            }
            byte[] data = Arrays.copyOfRange(body, dataStart, dataStart + size);
            if (major == 4) {
                if ((frameFlags & 0x0001) != 0 && data.length >= 4) { // data length indicator
                    data = Arrays.copyOfRange(data, 4, data.length);
                }
                if ((frameFlags & 0x0002) != 0) {
                    data = deunsynchronise(data);
                }
            }
            frames.add(new Frame(id, frameFlags, data));
            pos = dataStart + size;
        }
        return List.copyOf(frames);
    }

    /** Decodes the text of a text frame (encoding byte + text); several values are joined with "\0". */
    public static String text(Frame frame) {
        byte[] body = frame.body();
        if (body.length == 0) {
            return "";
        }
        return decode(body[0], Arrays.copyOfRange(body, 1, body.length)).replaceAll("\0+$", "");
    }

    /** The encoding byte of a text-like frame. */
    public static int encoding(Frame frame) {
        return frame.body().length == 0 ? -1 : frame.body()[0];
    }

    /** Decodes text in an ID3v2 encoding (0 Latin-1, 1 UTF-16 with BOM, 2 UTF-16BE, 3 UTF-8). */
    public static String decode(int encoding, byte[] data) {
        Charset charset = switch (encoding) {
            case 1 -> data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE
                    ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        byte[] text = data;
        if (encoding == 1 && data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            text = Arrays.copyOfRange(data, 2, data.length);
        }
        String decoded = new String(text, charset);
        // UTF-16 multi-values: each value has its own BOM
        return decoded.replace("\0﻿", "\0").replace("\0￾", "\0");
    }

    /** The fields of an ID3v1 block: title, artist, album, year, comment, track (or -1 for v1.0), genre. */
    public record V1Fields(String title, String artist, String album, String year, String comment, int track,
                           int genre) {
    }

    /** Decodes a 128-byte ID3v1 block (Latin-1, NUL/space padding stripped). */
    public static V1Fields v1Fields(byte[] block) {
        boolean v11 = block[125] == 0 && block[126] != 0;
        return new V1Fields(v1Text(block, 3, 30), v1Text(block, 33, 30), v1Text(block, 63, 30),
                v1Text(block, 93, 4), v1Text(block, 97, v11 ? 28 : 30), v11 ? block[126] & 0xFF : -1,
                block[127] & 0xFF);
    }

    private static String v1Text(byte[] block, int offset, int length) {
        String text = new String(block, offset, length, StandardCharsets.ISO_8859_1);
        int nul = text.indexOf('\0');
        return (nul >= 0 ? text.substring(0, nul) : text).stripTrailing();
    }

    static byte[] deunsynchronise(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        for (int i = 0; i < data.length; i++) {
            out.write(data[i]);
            if ((data[i] & 0xFF) == 0xFF && i + 1 < data.length && data[i + 1] == 0) {
                i++;
            }
        }
        return out.toByteArray();
    }

    private static int syncsafe(byte[] bytes, int offset) {
        return ((bytes[offset] & 0x7F) << 21) | ((bytes[offset + 1] & 0x7F) << 14)
                | ((bytes[offset + 2] & 0x7F) << 7) | (bytes[offset + 3] & 0x7F);
    }

    private static int int32(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
