package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyNumberTotal;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo;
import org.jaudiotagger.tag.id3.framebody.AbstractID3v2FrameBody;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;

/**
 * Applies {@link FieldChanges} to an ID3v2 tag at frame level, so no global jaudiotagger formatting (genre
 * references, zero padding) applies. Values: TRCK/TPOS "3/12" unpadded; TCON plain text, several genres
 * NUL-separated in ID3v2.4 and joined with "; " in ID3v2.2/2.3; YEAR in TYER (v2.3, with stale TDAT/TIME removed),
 * TDRC (v2.4), TYE (v2.2); COMMENT is the COMM frame with an empty description, other COMM frames stay.
 *
 * <p>Encoding of the written frames: AUTO = Latin-1 if the value fits, else UTF-16 (v2.2/2.3) or UTF-8 (v2.4);
 * LATIN1/UTF16/UTF8 as chosen.
 */
final class V2Fields {

    private static final byte LATIN1 = 0;
    private static final byte UTF16 = 1;
    private static final byte UTF8 = 3;

    private V2Fields() {
    }

    static void apply(AbstractID3v2Tag tag, int major, FieldChanges changes, V2Encoding encoding) {
        for (Map.Entry<Field, FieldValue> entry : changes.set().entrySet()) {
            set(tag, major, entry.getKey(), entry.getValue(), encoding);
        }
        for (Field field : changes.clear()) {
            clear(tag, major, field);
        }
    }

    private static String id(Field field, int major) {
        boolean v22 = major == 2;
        return switch (field) {
            case TITLE -> v22 ? "TT2" : "TIT2";
            case ARTIST -> v22 ? "TP1" : "TPE1";
            case ALBUM_ARTIST -> v22 ? "TP2" : "TPE2";
            case ALBUM -> v22 ? "TAL" : "TALB";
            case YEAR -> v22 ? "TYE" : major == 4 ? "TDRC" : "TYER";
            case TRACK -> v22 ? "TRK" : "TRCK";
            case DISC -> v22 ? "TPA" : "TPOS";
            case GENRE -> v22 ? "TCO" : "TCON";
            case COMMENT -> v22 ? "COM" : "COMM";
        };
    }

    private static void set(AbstractID3v2Tag tag, int major, Field field, FieldValue value, V2Encoding encoding) {
        String text = switch (value) {
            case FieldValue.Genres genres -> genres.values().stream().map(Genre::name)
                    .collect(Collectors.joining(major == 4 ? "\0" : "; "));
            default -> value.display();
        };
        byte enc = encoding(text, major, encoding);
        String id = id(field, major);
        if (field == Field.COMMENT) {
            setComment(tag, id, text, enc);
            return;
        }
        if (field == Field.YEAR) {
            clearYearFrames(tag, major);
        }
        AbstractID3v2Frame frame = tag.createFrame(id);
        AbstractID3v2FrameBody body = (AbstractID3v2FrameBody) frame.getBody();
        body.setTextEncoding(enc);
        switch (body) {
            case AbstractFrameBodyNumberTotal numberTotal -> numberTotal.setText(text);
            case AbstractFrameBodyTextInfo textInfo -> textInfo.setText(text);
            default -> throw new IllegalStateException("unexpected body for " + id + ": " + body.getClass());
        }
        tag.setFrame(frame);
    }

    private static void setComment(AbstractID3v2Tag tag, String id, String text, byte enc) {
        List<TagField> comments = new ArrayList<>(frames(tag, id));
        boolean replaced = false;
        for (TagField field : comments) {
            if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyCOMM comm
                    && comm.getDescription().isEmpty()) {
                comm.setTextEncoding(enc);
                comm.setText(text);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            AbstractID3v2Frame frame = tag.createFrame(id);
            FrameBodyCOMM comm = (FrameBodyCOMM) frame.getBody();
            comm.setTextEncoding(enc);
            comm.setLanguage("eng");
            comm.setDescription("");
            comm.setText(text);
            comments.add(frame);
        }
        tag.setFrame(id, comments);
    }

    private static void clear(AbstractID3v2Tag tag, int major, Field field) {
        String id = id(field, major);
        if (field == Field.COMMENT) {
            List<TagField> keep = new ArrayList<>();
            for (TagField existing : frames(tag, id)) {
                if (!(existing instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyCOMM comm
                        && comm.getDescription().isEmpty())) {
                    keep.add(existing);
                }
            }
            if (keep.isEmpty()) {
                tag.removeFrame(id);
            } else {
                tag.setFrame(id, keep);
            }
        } else if (field == Field.YEAR) {
            clearYearFrames(tag, major);
        } else {
            tag.removeFrame(id);
        }
    }

    /** A new year makes the old date/time frames stale; the reader's fallback frame of the other version too. */
    private static void clearYearFrames(AbstractID3v2Tag tag, int major) {
        if (major == 2) {
            tag.removeFrame("TYE");
            tag.removeFrame("TDA");
            tag.removeFrame("TIM");
        } else {
            for (String id : List.of("TYER", "TDAT", "TIME", "TDRC", "TYERTDAT")) {
                tag.removeFrame(id);
            }
        }
    }

    /** The frames with this id; jaudiotagger returns null instead of an empty list. */
    private static List<TagField> frames(AbstractID3v2Tag tag, String id) {
        List<TagField> frames = tag.getFrame(id);
        return frames == null ? List.of() : frames;
    }

    private static byte encoding(String text, int major, V2Encoding encoding) {
        return switch (encoding) {
            case LATIN1 -> LATIN1;
            case UTF16 -> UTF16;
            case UTF8 -> UTF8;
            case AUTO -> text.chars().allMatch(c -> c <= 0xFF) ? LATIN1 : major == 4 ? UTF8 : UTF16;
        };
    }
}
