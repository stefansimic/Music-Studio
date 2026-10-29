package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * A parsed output pattern: each segment becomes folders and the file name (last segment) of an export.
 *
 * @param source   the text as the user typed it; not null, not blank
 * @param segments not empty, no nulls
 */
public record OutputPattern(String source, List<PatternSegment> segments) {

    public OutputPattern {
        Require.nonBlank(source, "source");
        Require.noNullElements(segments, "segments");
        Require.that(!segments.isEmpty(), "a pattern needs at least one segment");
        segments = List.copyOf(segments);
    }
}
