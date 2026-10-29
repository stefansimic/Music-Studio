package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3Tags;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyNumberTotal;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;

/**
 * Maps core's write request to jaudiotagger: converts the ID3v2 tag to the chosen version, sets the changed fields at
 * frame level (so the stored text is exactly what core planned and the text encoding is explicit), reports what the
 * conversion drops, and writes the ID3v1 block byte for byte.
 */
final class Id3Writing {

    private static final int V1_LENGTH = 128;

    private Id3Writing() {}

    /** Frame ids of the fields core can write, per ID3v2 version (index 2, 3, 4). */
    private record Ids(String title, String artist, String albumArtist, String album, String year, String track,
                       String disc, String genre, String comment) {
        static final Ids V2_2 = new Ids("TT2", "TP1", "TP2", "TAL", "TYE", "TRK", "TPA", "TCO", "COM");
        static final Ids V2_3 = new Ids("TIT2", "TPE1", "TPE2", "TALB", "TYER", "TRCK", "TPOS", "TCON", "COMM");
        static final Ids V2_4 = new Ids("TIT2", "TPE1", "TPE2", "TALB", "TDRC", "TRCK", "TPOS", "TCON", "COMM");

        String of(Field field) {
            return switch (field) {
                case TITLE -> title;
                case ARTIST -> artist;
                case ALBUM_ARTIST -> albumArtist;
                case ALBUM -> album;
                case YEAR -> year;
                case TRACK -> track;
                case DISC -> disc;
                case GENRE -> genre;
                case COMMENT -> comment;
            };
        }
    }

    static int versionNumber(AbstractID3v2Tag tag) {
        return tag instanceof ID3v24Tag ? 4 : tag instanceof ID3v23Tag ? 3 : 2;
    }

    private static Ids idsOf(AbstractID3v2Tag tag) {
        return switch (versionNumber(tag)) {
            case 4 -> Ids.V2_4;
            case 3 -> Ids.V2_3;
            default -> Ids.V2_2;
        };
    }

    // ---- ID3v2 conversion -----------------------------------------------------------------------------------------

    /** @return the tag in the chosen version: the same tag if it already has it, a converted copy otherwise */
    static AbstractID3v2Tag convert(AbstractID3v2Tag existing, Id3v2Choice choice) {
        return switch (choice) {
            case V2_2 -> existing instanceof ID3v22Tag same ? same
                    : existing == null ? new ID3v22Tag() : new ID3v22Tag(existing);
            case V2_3 -> existing instanceof ID3v23Tag same ? same
                    : existing == null ? new ID3v23Tag() : new ID3v23Tag(existing);
            case V2_4 -> existing instanceof ID3v24Tag same ? same
                    : existing == null ? new ID3v24Tag() : new ID3v24Tag(existing);
            case NONE -> throw new IllegalArgumentException("no ID3v2 version chosen");
        };
    }

    /** @return the ids of all frames of the tag, one entry per frame, in order */
    private static List<String> frameIds(AbstractID3v2Tag tag) {
        List<String> ids = new ArrayList<>();
        Iterator<TagField> fields = tag.getFields();
        while (fields.hasNext()) {
            if (fields.next() instanceof AbstractID3v2Frame frame) {
                ids.add(frame.getId());
            }
        }
        return ids;
    }

    /**
     * Frames of {@code existing} that {@code converted} no longer has, by the id they would have after conversion.
     * Frames of fields core writes are excluded: they are handled as fields.
     */
    static List<String> droppedFrames(AbstractID3v2Tag existing, AbstractID3v2Tag converted) {
        if (existing == converted) {
            return List.of();
        }
        Set<String> fieldIds = new LinkedHashSet<>();
        for (Field field : Field.values()) {
            fieldIds.add(Ids.V2_2.of(field));
            fieldIds.add(Ids.V2_3.of(field));
            fieldIds.add(Ids.V2_4.of(field));
        }
        Set<String> kept = new LinkedHashSet<>(frameIds(converted));
        List<String> dropped = new ArrayList<>();
        for (String id : frameIds(existing)) {
            if (fieldIds.contains(id)) {
                continue;
            }
            String mapped = mapId(versionNumber(existing), versionNumber(converted), id);
            if (!kept.contains(mapped == null ? id : mapped) && !dropped.contains(id)) {
                dropped.add(id);
            }
        }
        return dropped;
    }

    private static String mapId(int from, int to, String id) {
        if (id.equals("APIC") || id.equals("PIC")) { // pictures are stored as PIC in v2.2 and as APIC otherwise
            return to == 2 ? "PIC" : "APIC";
        }
        String current = id;
        int version = from;
        while (version != to) {
            String next;
            if (version < to) {
                next = version == 2 ? ID3Tags.convertFrameID22To23(current) : ID3Tags.convertFrameID23To24(current);
                version++;
            } else {
                next = version == 4 ? ID3Tags.convertFrameID24To23(current) : ID3Tags.convertFrameID23To22(current);
                version--;
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    static Issue droppedIssue(List<String> frames) {
        return Issue.warning(IssueCode.FRAME_NOT_REPRESENTABLE,
                "These frames have no equivalent in the chosen ID3v2 version and are left out: "
                        + String.join(", ", frames));
    }

    /**
     * Frames the library converted as they were: several genres in a v2.4 frame are NUL-separated, but ID3v2.2 and
     * ID3v2.3 hold them as "A; B".
     */
    static void joinGenres(AbstractID3v2Tag tag) {
        if (tag instanceof ID3v24Tag) {
            return;
        }
        List<TagField> frames = tag.getFrame(idsOf(tag).genre());
        if (frames == null) {
            return;
        }
        for (TagField field : frames) {
            if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof AbstractFrameBodyTextInfo info) {
                String text = info.getText();
                if (text != null && text.indexOf('\u0000') >= 0) {
                    List<String> parts = new ArrayList<>();
                    for (String part : text.split("\u0000")) {
                        if (!part.isBlank()) {
                            parts.add(part);
                        }
                    }
                    info.setText(String.join("; ", parts));
                }
            }
        }
    }

    // ---- field changes --------------------------------------------------------------------------------------------

    static void apply(AbstractID3v2Tag tag, FieldChanges changes, V2Encoding encoding) throws FieldDataInvalidException {
        Ids ids = idsOf(tag);
        boolean v24 = tag instanceof ID3v24Tag;
        for (Field field : changes.clear()) {
            clear(tag, ids, field);
        }
        for (Map.Entry<Field, FieldValue> entry : changes.set().entrySet()) {
            set(tag, ids, v24, encoding, entry.getKey(), entry.getValue());
        }
    }

    private static void clear(AbstractID3v2Tag tag, Ids ids, Field field) {
        if (field == Field.COMMENT) {
            replaceUndescribedComments(tag, ids, null);
        } else {
            tag.removeFrame(ids.of(field));
        }
    }

    private static void set(AbstractID3v2Tag tag, Ids ids, boolean v24, V2Encoding encoding, Field field,
                            FieldValue value) throws FieldDataInvalidException {
        String text = switch (value) {
            case FieldValue.Genres genres -> String.join(v24 ? "\u0000" : "; ",
                    genres.values().stream().map(g -> g.name()).toList());
            default -> value.display();
        };
        if (field == Field.COMMENT) {
            replaceUndescribedComments(tag, ids, textFrame(tag, ids.of(field), text, v24, encoding));
            return;
        }
        AbstractID3v2Frame frame = textFrame(tag, ids.of(field), text, v24, encoding);
        tag.removeFrame(ids.of(field)); // otherwise the library merges the old track/disc total into the new frame
        tag.setField(frame);
    }

    private static AbstractID3v2Frame textFrame(AbstractID3v2Tag tag, String id, String text, boolean v24,
                                                V2Encoding encoding) {
        AbstractID3v2Frame frame = tag.createFrame(id);
        byte textEncoding = encodingFor(text, v24, encoding);
        switch (frame.getBody()) {
            case FrameBodyCOMM comment -> {
                comment.setDescription("");
                comment.setText(text);
                comment.setTextEncoding(textEncoding);
            }
            case AbstractFrameBodyNumberTotal numberTotal -> {
                numberTotal.setText(text);
                numberTotal.setTextEncoding(textEncoding);
            }
            case AbstractFrameBodyTextInfo info -> {
                info.setText(text);
                info.setTextEncoding(textEncoding);
            }
            default -> throw new IllegalStateException("frame " + id + " is not a text frame");
        }
        return frame;
    }

    /** Text encoding byte: as chosen; AUTO is Latin-1 if every character fits, otherwise UTF-8 (v2.4) or UTF-16. */
    static byte encodingFor(String text, boolean v24, V2Encoding encoding) {
        return switch (encoding) {
            case LATIN1 -> TextEncoding.ISO_8859_1;
            case UTF16 -> TextEncoding.UTF_16;
            case UTF8 -> TextEncoding.UTF_8;
            case AUTO -> StandardCharsets.ISO_8859_1.newEncoder().canEncode(text) ? TextEncoding.ISO_8859_1
                    : v24 ? TextEncoding.UTF_8 : TextEncoding.UTF_16;
        };
    }

    /** Replaces the comment frames without description by {@code replacement}; frames with a description stay. */
    private static void replaceUndescribedComments(AbstractID3v2Tag tag, Ids ids, AbstractID3v2Frame replacement) {
        List<TagField> kept = new ArrayList<>();
        List<TagField> existing = tag.getFrame(ids.comment());
        if (existing != null) {
            for (TagField field : existing) {
                if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyCOMM comment
                        && comment.getDescription() != null && !comment.getDescription().isEmpty()) {
                    kept.add(field);
                }
            }
        }
        tag.removeFrame(ids.comment());
        if (replacement != null) {
            kept.add(0, replacement);
        }
        if (!kept.isEmpty()) {
            tag.setFrame(ids.comment(), kept);
        }
    }

    /**
     * Removes the ID3v2 tag(s) at the start of the file by shifting the audio to the front. jaudiotagger's own delete
     * only blanks the "ID3" marker and leaves the rest of the tag in front of the audio.
     */
    static void stripV2(RandomAccessFile file) throws IOException {
        long start = 0;
        byte[] header = new byte[10];
        while (true) {
            file.seek(start);
            if (file.length() - start < 10) {
                break;
            }
            file.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                break;
            }
            long size = ((header[6] & 0x7FL) << 21) | ((header[7] & 0x7FL) << 14) | ((header[8] & 0x7FL) << 7)
                    | (header[9] & 0x7FL);
            boolean footer = header[3] == 4 && (header[5] & 0x10) != 0;
            start += 10 + size + (footer ? 10 : 0);
            if (start > file.length()) {
                throw new IOException("the ID3v2 header declares more bytes than the file has");
            }
        }
        if (start == 0) {
            return;
        }
        byte[] buffer = new byte[1 << 16];
        long read = start;
        long written = 0;
        long length = file.length();
        while (read < length) {
            file.seek(read);
            int n = file.read(buffer, 0, (int) Math.min(buffer.length, length - read));
            if (n < 0) {
                throw new IOException("unexpected end of file while removing the ID3v2 tag");
            }
            file.seek(written);
            file.write(buffer, 0, n);
            read += n;
            written += n;
        }
        file.setLength(written);
    }

    // ---- ID3v1 ----------------------------------------------------------------------------------------------------

    /** @return the 128-byte ID3v1 block for these values, byte for byte */
    static byte[] v1Block(Id3v1Values values) {
        byte[] block = new byte[V1_LENGTH];
        block[0] = 'T';
        block[1] = 'A';
        block[2] = 'G';
        Charset charset = values.encoding().charset();
        put(block, 3, 30, values.title(), charset);
        put(block, 33, 30, values.artist(), charset);
        put(block, 63, 30, values.album(), charset);
        if (values.year().isPresent()) {
            put(block, 93, 4, String.format("%04d", values.year().getAsInt()), StandardCharsets.US_ASCII);
        }
        if (values.version() == Id3v1Choice.V1_1) {
            put(block, 97, 28, values.comment(), charset);
            block[126] = (byte) values.track();
        } else {
            put(block, 97, 30, values.comment(), charset);
        }
        block[127] = (byte) values.genre();
        return block;
    }

    private static void put(byte[] block, int offset, int length, String text, Charset charset) {
        byte[] bytes = text.getBytes(charset);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, length));
    }

    /**
     * SHA-256 of the audio region: everything after the leading ID3v2 tag(s) and before a trailing ID3v1 tag. Used to
     * prove that a tag edit did not touch the audio.
     */
    static byte[] audioHash(java.nio.file.Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long start = 0;
            byte[] header = new byte[10];
            while (file.length() - start >= 10) {
                file.seek(start);
                file.readFully(header);
                if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                    break;
                }
                long size = ((header[6] & 0x7FL) << 21) | ((header[7] & 0x7FL) << 14) | ((header[8] & 0x7FL) << 7)
                        | (header[9] & 0x7FL);
                start += 10 + size + (header[3] == 4 && (header[5] & 0x10) != 0 ? 10 : 0);
            }
            long end = hasV1(file) ? file.length() - V1_LENGTH : file.length();
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            file.seek(Math.min(start, end));
            long remaining = Math.max(0, end - start);
            while (remaining > 0) {
                int n = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) {
                    break;
                }
                digest.update(buffer, 0, n);
                remaining -= n;
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return true if the file ends with an ID3v1 tag */
    static boolean hasV1(RandomAccessFile file) throws IOException {
        if (file.length() < V1_LENGTH) {
            return false;
        }
        file.seek(file.length() - V1_LENGTH);
        byte[] header = new byte[3];
        file.readFully(header);
        return header[0] == 'T' && header[1] == 'A' && header[2] == 'G';
    }

    /** Writes, replaces or removes the trailing ID3v1 tag. {@code values == null && !remove} leaves the file as is. */
    static void writeV1(RandomAccessFile file, Id3v1Values values, boolean remove) throws IOException {
        // text is already fitted by core (single-byte charsets: characters = bytes); a tag is appended after any
        // APE/Lyrics3 trailer, which is legal
        boolean present = hasV1(file);
        if (values != null) {
            file.seek(present ? file.length() - V1_LENGTH : file.length());
            file.write(v1Block(values));
        } else if (remove && present) {
            file.setLength(file.length() - V1_LENGTH);
        }
    }
}
