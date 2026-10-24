package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand-written ID3 byte encoder for edge-case test files. It is deliberately independent of every tagging library
 * (ours and the one under test), so expectations come from the ID3 specification, not from a reader.
 *
 * <p>Only builds byte arrays; writing them happens in the test, inside a {@code @TempDir}.
 */
public final class Id3Bytes {

    /** ID3v2 text encoding bytes. */
    public enum Enc {
        LATIN1(0, StandardCharsets.ISO_8859_1, 1),
        /** UTF-16 with little-endian BOM (FF FE). */
        UTF16_BOM_LE(1, StandardCharsets.UTF_16LE, 2),
        /** UTF-16 with big-endian BOM (FE FF). */
        UTF16_BOM_BE(1, StandardCharsets.UTF_16BE, 2),
        /** Encoding byte 1 (UTF-16) but no BOM, little-endian bytes (seen in the wild). */
        UTF16_NO_BOM_LE(1, StandardCharsets.UTF_16LE, 2),
        /** v2.4 encoding byte 2: UTF-16BE without BOM. */
        UTF16BE(2, StandardCharsets.UTF_16BE, 2),
        /** v2.4 encoding byte 3. */
        UTF8(3, StandardCharsets.UTF_8, 1);

        final int id;
        final Charset charset;
        final int terminatorLength;

        Enc(int id, Charset charset, int terminatorLength) {
            this.id = id;
            this.charset = charset;
            this.terminatorLength = terminatorLength;
        }

        byte[] encode(String text) {
            byte[] body = text.getBytes(charset);
            byte[] bom = switch (this) {
                case UTF16_BOM_LE -> new byte[] {(byte) 0xFF, (byte) 0xFE};
                case UTF16_BOM_BE -> new byte[] {(byte) 0xFE, (byte) 0xFF};
                default -> new byte[0];
            };
            return concat(bom, body);
        }

        byte[] terminator() {
            return new byte[terminatorLength];
        }
    }

    private Id3Bytes() {
    }

    // --- ID3v2 frames ---------------------------------------------------------------------------------------

    /** A frame: id and body. The header is written by {@link #v2Tag}. */
    public record Frame(String id, byte[] body) {
    }

    /** Text frame (T***) with one or more values; several values are NUL-separated (v2.4 style). */
    public static Frame text(String id, Enc enc, String... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(enc.id);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.writeBytes(enc.terminator());
            }
            out.writeBytes(enc.encode(values[i]));
        }
        return new Frame(id, out.toByteArray());
    }

    /** Text frame with a trailing NUL terminator (allowed by the spec, common in real files). */
    public static Frame textTerminated(String id, Enc enc, String value) {
        Frame frame = text(id, enc, value);
        return new Frame(id, concat(frame.body(), enc.terminator()));
    }

    /** COMM / USLT style frame: encoding, language, description, text. */
    public static Frame langText(String id, Enc enc, String lang, String description, String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(enc.id);
        out.writeBytes(lang.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(enc.encode(description));
        out.writeBytes(enc.terminator());
        out.writeBytes(enc.encode(text));
        return new Frame(id, out.toByteArray());
    }

    /** TXXX frame. */
    public static Frame txxx(String description, String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Enc.LATIN1.id);
        out.writeBytes(description.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0);
        out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
        return new Frame("TXXX", out.toByteArray());
    }

    /** Owner-id + binary frame (PRIV, UFID). */
    public static Frame ownerData(String id, String owner, byte[] data) {
        return new Frame(id, concat(owner.getBytes(StandardCharsets.ISO_8859_1), new byte[] {0}, data));
    }

    /** APIC (v2.3/v2.4). */
    public static Frame apic(String mime, int pictureType, String description, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Enc.LATIN1.id);
        out.writeBytes(mime.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0);
        out.write(pictureType);
        out.writeBytes(description.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0);
        out.writeBytes(data);
        return new Frame("APIC", out.toByteArray());
    }

    /** PIC (v2.2): 3-character image format instead of a MIME type. */
    public static Frame pic(String format, int pictureType, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Enc.LATIN1.id);
        out.writeBytes(format.getBytes(StandardCharsets.ISO_8859_1));
        out.write(pictureType);
        out.write(0); // empty description
        out.writeBytes(data);
        return new Frame("PIC", out.toByteArray());
    }

    /** Any frame with raw body bytes (unknown/experimental frames). */
    public static Frame raw(String id, byte[] body) {
        return new Frame(id, body);
    }

    // --- ID3v2 tag ------------------------------------------------------------------------------------------

    /**
     * A complete ID3v2 tag.
     *
     * @param major   2, 3 or 4
     * @param frames  the frames in order
     * @param padding number of zero bytes after the frames
     */
    public static byte[] v2Tag(int major, List<Frame> frames, int padding) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Frame frame : frames) {
            body.writeBytes(frameHeader(major, frame.id(), frame.body().length));
            body.writeBytes(frame.body());
        }
        body.writeBytes(new byte[padding]);
        return concat(v2Header(major, 0, body.size()), body.toByteArray());
    }

    /** An ID3v2.3 tag with the unsynchronisation flag set; the whole tag body is unsynchronised. */
    public static byte[] v23TagUnsynchronised(List<Frame> frames, int padding) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Frame frame : frames) {
            body.writeBytes(frameHeader(3, frame.id(), frame.body().length));
            body.writeBytes(frame.body());
        }
        body.writeBytes(new byte[padding]);
        byte[] unsync = unsynchronise(body.toByteArray());
        return concat(v2Header(3, 0x80, unsync.length), unsync);
    }

    /** An ID3v2 header with an arbitrary declared size (for damaged tags). */
    public static byte[] v2Header(int major, int flags, int size) {
        return concat(new byte[] {'I', 'D', '3', (byte) major, 0, (byte) flags}, syncsafe(size));
    }

    /** A frame header for the given version. */
    public static byte[] frameHeader(int major, String id, int size) {
        byte[] idBytes = id.getBytes(StandardCharsets.ISO_8859_1);
        return switch (major) {
            case 2 -> concat(idBytes, new byte[] {(byte) (size >> 16), (byte) (size >> 8), (byte) size});
            case 3 -> concat(idBytes, int32(size), new byte[2]);
            case 4 -> concat(idBytes, syncsafe(size), new byte[2]);
            default -> throw new IllegalArgumentException("major " + major);
        };
    }

    // --- ID3v1 ----------------------------------------------------------------------------------------------

    /**
     * A 128-byte ID3v1 block, Latin-1. {@code track == null} writes ID3v1.0 (30-byte comment), otherwise ID3v1.1.
     */
    public static byte[] v1Tag(String title, String artist, String album, String year, String comment,
                               Integer track, int genre) {
        return v1Tag(title, artist, album, year, comment, track, genre, (byte) 0);
    }

    /** As {@link #v1Tag(String, String, String, String, String, Integer, int)} with a custom padding byte. */
    public static byte[] v1Tag(String title, String artist, String album, String year, String comment,
                               Integer track, int genre, byte pad) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("TAG".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(field(title, 30, pad));
        out.writeBytes(field(artist, 30, pad));
        out.writeBytes(field(album, 30, pad));
        out.writeBytes(field(year, 4, pad));
        if (track == null) {
            out.writeBytes(field(comment, 30, pad));
        } else {
            out.writeBytes(field(comment, 28, pad));
            out.write(0);
            out.write(track);
        }
        out.write(genre);
        byte[] block = out.toByteArray();
        if (block.length != 128) {
            throw new IllegalStateException("v1 block has " + block.length + " bytes");
        }
        return block;
    }

    private static byte[] field(String text, int size, byte pad) {
        byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
        if (data.length > size) {
            throw new IllegalArgumentException("too long for " + size + " bytes: " + text);
        }
        byte[] result = new byte[size];
        Arrays.fill(result, pad);
        System.arraycopy(data, 0, result, 0, data.length);
        return result;
    }

    // --- helpers --------------------------------------------------------------------------------------------

    /** The audio part (MPEG frames) of the untagged fixture: 1 s silence, 128 kbit/s CBR. */
    public static byte[] audio() {
        return Fixtures.bytes(Fixture.NO_TAGS);
    }

    /** A minimal valid-looking JPEG of the given total size (SOI, filler, EOI). */
    public static byte[] jpeg(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        for (int i = 2; i < size - 2; i++) {
            data[i] = (byte) (i * 31); // contains 0xFF bytes, exercises unsynchronisation
        }
        data[size - 2] = (byte) 0xFF;
        data[size - 1] = (byte) 0xD9;
        return data;
    }

    /** PNG signature followed by filler bytes. */
    public static byte[] png(int size) {
        byte[] data = new byte[size];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(sig, 0, data, 0, sig.length);
        for (int i = sig.length; i < size; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    public static List<Frame> frames(Frame... frames) {
        return new ArrayList<>(List.of(frames));
    }

    static byte[] syncsafe(int n) {
        return new byte[] {(byte) ((n >> 21) & 0x7F), (byte) ((n >> 14) & 0x7F), (byte) ((n >> 7) & 0x7F),
                (byte) (n & 0x7F)};
    }

    static byte[] int32(int n) {
        return new byte[] {(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    static byte[] unsynchronise(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            out.write(b);
            if (b == 0xFF && (i + 1 == data.length || (data[i + 1] & 0xE0) == 0xE0 || data[i + 1] == 0)) {
                out.write(0);
            }
        }
        return out.toByteArray();
    }
}
