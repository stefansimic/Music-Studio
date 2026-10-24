package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A parsed input pattern such as {@code {album}/{artist} - {title} - {year}}. The last segment matches the file name
 * without extension, the one before it the parent folder, and so on. Create it with
 * {@link PatternParser#parseInput}.
 *
 * @param source   the pattern text as entered; not null
 * @param segments path levels, top first; non-empty; no placeholder with padding; no field twice; copied
 */
public record InputPattern(String source, List<PatternSegment> segments) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if there is no segment, a placeholder has a padding width, or a field occurs
     *                                  twice
     */
    public InputPattern {
        Require.nonNull(source, "source");
        Require.noNullElements(segments, "segments");
        Require.that(!segments.isEmpty(), "a pattern needs at least one segment");
        Set<Field> seen = EnumSet.noneOf(Field.class);
        for (PatternSegment segment : segments) {
            for (Token token : segment.tokens()) {
                if (token instanceof Token.Placeholder placeholder) {
                    Require.that(placeholder.padWidth().isEmpty(), "input patterns have no padding width");
                    Require.that(seen.add(placeholder.field()), "placeholder {%s} occurs twice",
                            placeholder.field().placeholder());
                }
            }
        }
        segments = List.copyOf(segments);
    }

    /** @return the fields of the placeholders in pattern order (without {@code {*}}); unmodifiable, never null */
    public List<Field> placeholders() {
        List<Field> fields = new ArrayList<>();
        for (PatternSegment segment : segments) {
            for (Token token : segment.tokens()) {
                if (token instanceof Token.Placeholder placeholder) {
                    fields.add(placeholder.field());
                }
            }
        }
        return List.copyOf(fields);
    }
}
