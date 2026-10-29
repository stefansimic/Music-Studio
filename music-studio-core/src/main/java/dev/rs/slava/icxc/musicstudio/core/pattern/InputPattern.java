package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * A parsed input pattern: each segment matches the file name (last segment) and its folders (the segments before it).
 *
 * @param source   the text as the user typed it; not null, not blank
 * @param segments not empty, no nulls
 */
public record InputPattern(String source, List<PatternSegment> segments) {

    public InputPattern {
        Require.nonBlank(source, "source");
        Require.noNullElements(segments, "segments");
        Require.that(!segments.isEmpty(), "a pattern needs at least one segment");
        segments = List.copyOf(segments);
    }
}
