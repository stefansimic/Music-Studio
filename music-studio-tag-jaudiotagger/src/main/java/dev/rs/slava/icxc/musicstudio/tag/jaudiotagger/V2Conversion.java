package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.AggregatedFrame;
import org.jaudiotagger.tag.id3.ID3Frames;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.framebody.FrameBodyUnsupported;

/**
 * Converts a parsed ID3v2 tag to the target version and reports every source frame that does not survive.
 * Used by both {@code preflight} and {@code writeCopy}, so a write never drops a frame preflight did not report.
 *
 * <p>Corrections of jaudiotagger's conversion (pinned by {@code JaudiotaggerLearningTest}): unknown frames whose ID
 * cannot exist in the target version are removed (the library truncates {@code XABC} to {@code XAB}); NUL-separated
 * multi-values of ID3v2.4 are joined with {@code "; "} for ID3v2.2/2.3.
 */
final class V2Conversion {

    /** The converted tag and the frames lost on the way. */
    record Result(AbstractID3v2Tag tag, List<Issue> lost) {
    }

    private V2Conversion() {
    }

    static int major(AbstractID3v2Tag tag) {
        return switch (tag) {
            case ID3v22Tag ignored -> 2;
            case ID3v24Tag ignored -> 4;
            default -> 3;
        };
    }

    static AbstractID3v2Tag empty(int major) {
        return switch (major) {
            case 2 -> new ID3v22Tag();
            case 4 -> new ID3v24Tag();
            default -> new ID3v23Tag();
        };
    }

    /** Converts {@code source} to {@code targetMajor}; the source object is not modified. */
    static Result convert(AbstractID3v2Tag source, int targetMajor) {
        int sourceMajor = major(source);
        if (sourceMajor == targetMajor) {
            return new Result(source, List.of());
        }
        AbstractID3v2Tag converted = switch (targetMajor) {
            case 2 -> new ID3v22Tag(source);
            case 4 -> new ID3v24Tag(source);
            default -> new ID3v23Tag(source);
        };
        if (sourceMajor == 2 || targetMajor == 2) {
            removeUnknownFrames(converted);
        }
        if (targetMajor < 4) {
            joinMultiValues(converted);
        }
        return new Result(converted, lostFrames(source, sourceMajor, converted, targetMajor));
    }

    /**
     * Unknown frames cannot cross the 3/4-character boundary of ID3v2.2 (the library truncates {@code XABC} to
     * {@code XAB}); they are removed and reported as lost.
     */
    private static void removeUnknownFrames(AbstractID3v2Tag tag) {
        List<String> remove = new ArrayList<>();
        for (AbstractID3v2Frame frame : frames(tag)) {
            if (frame.getBody() instanceof FrameBodyUnsupported) {
                remove.add(frame.getIdentifier());
            }
        }
        remove.forEach(tag::removeFrame);
    }

    private static void joinMultiValues(AbstractID3v2Tag tag) {
        for (AbstractID3v2Frame frame : frames(tag)) {
            if (frame.getBody() instanceof AbstractFrameBodyTextInfo text && !(text instanceof FrameBodyTXXX)) {
                String value = text.getText();
                String trimmed = value.replaceAll("\0+$", "");
                if (trimmed.indexOf('\0') >= 0) {
                    text.setText(String.join("; ", trimmed.split("\0+")));
                }
            }
        }
    }

    private static List<Issue> lostFrames(AbstractID3v2Tag source, int sourceMajor, AbstractID3v2Tag converted,
                                          int targetMajor) {
        Map<String, Integer> available = new HashMap<>();
        for (AbstractID3v2Frame frame : frames(converted)) {
            available.merge(frame.getIdentifier(), 1, Integer::sum);
        }
        Map<String, Integer> needed = new HashMap<>();
        List<Issue> lost = new ArrayList<>();
        for (AbstractID3v2Frame frame : frames(source)) {
            String id = frame.getIdentifier();
            String mapped = map(id, sourceMajor, targetMajor);
            int count = needed.merge(id + "→" + mapped, 1, Integer::sum);
            if (mapped == null || available.getOrDefault(mapped, 0) < count) {
                lost.add(Issue.warning(IssueCode.FRAME_NOT_REPRESENTABLE, "frame " + id + " has no equivalent in "
                        + "ID3v2." + targetMajor + " and is not written"));
            }
        }
        return List.copyOf(lost);
    }

    /** The frame ID in the target version, or null if there is none. */
    private static String map(String id, int from, int to) {
        String current = id;
        int version = from;
        while (version != to && current != null) {
            int next = version < to ? version + 1 : version - 1;
            current = step(current, version, next);
            version = next;
        }
        return current;
    }

    private static String step(String id, int from, int to) {
        // date frames are converted specially by jaudiotagger (TDRC <-> TYER + TDAT + TIME)
        if (from == 4 && to == 3 && id.equals("TDRC")) {
            return "TYER";
        }
        if (from == 3 && to == 4 && (id.equals("TYER") || id.equals("TDAT") || id.equals("TIME"))) {
            return "TDRC";
        }
        Map<String, String> convert;
        Map<String, String> force;
        if (from == 2) {
            convert = ID3Frames.convertv22Tov23;
            force = ID3Frames.forcev22Tov23;
        } else if (from == 3 && to == 2) {
            convert = ID3Frames.convertv23Tov22;
            force = ID3Frames.forcev23Tov22;
        } else if (from == 3) {
            convert = ID3Frames.convertv23Tov24;
            force = ID3Frames.forcev23Tov24;
        } else {
            convert = ID3Frames.convertv24Tov23;
            force = ID3Frames.forcev24Tov23;
        }
        String mapped = convert.get(id);
        if (mapped == null) {
            mapped = force.get(id);
        }
        if (mapped != null) {
            return mapped;
        }
        // same ID valid in both 4-character versions (most frames, also unknown ones); none to/from ID3v2.2
        return from != 2 && to != 2 ? id : null;
    }

    /** All frames, aggregated frames (TYER+TDAT) expanded. */
    static List<AbstractID3v2Frame> frames(AbstractID3v2Tag tag) {
        List<AbstractID3v2Frame> frames = new ArrayList<>();
        Iterator<TagField> fields = tag.getFields();
        while (fields.hasNext()) {
            TagField field = fields.next();
            if (field instanceof AbstractID3v2Frame frame) {
                frames.add(frame);
            } else if (field instanceof AggregatedFrame aggregated) {
                frames.addAll(aggregated.getFrames());
            }
        }
        return frames;
    }
}
