package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.text.Id3v1Text;
import dev.rs.slava.icxc.musicstudio.core.text.Transliterator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a {@code TagReader} found in one file.
 *
 * <p><b>Read-only view:</b> the values are for display and comparison, not for writing back. A multi-valued frame
 * (for example several artists) is merged into one display value, and an unreadable ID3v2 tag yields no v2 layer
 * together with a {@code TAG_DAMAGED} issue. "No v2 layer" therefore never means "no v2 data"; a writer must not
 * derive tag content from this record alone.
 *
 * @param format  not null
 * @param layers  at most one layer per {@link TagFamily}, sorted by family order, every version belongs to a family
 *                valid for {@code format}; empty for a file without tags
 * @param stream  present if the stream headers could be parsed
 * @param cover   describes the front cover, or the first picture of any type (then {@code OTHER})
 * @param issues  warnings found while reading; no nulls
 */
public record TagReadResult(MediaFormat format, List<TagLayer> layers, Optional<StreamInfo> stream,
                            Optional<CoverInfo> cover, List<Issue> issues) {

    private static final int V1_TEXT_BYTES = 30;
    private static final int V1_1_COMMENT_BYTES = 28;

    public TagReadResult {
        Require.nonNull(format, "format");
        Require.noNullElements(layers, "layers");
        Require.nonNull(stream, "stream");
        Require.nonNull(cover, "cover");
        Require.noNullElements(issues, "issues");
        layers = List.copyOf(layers);
        issues = List.copyOf(issues);
        Set<TagFamily> seen = EnumSet.noneOf(TagFamily.class);
        for (TagLayer layer : layers) {
            TagFamily family = layer.version().family();
            Require.that(format.tagFamilies().contains(family), "%s has no %s tag", format, family);
            Require.that(seen.add(family), "duplicate layer for %s", family);
        }
        for (int i = 1; i < layers.size(); i++) {
            Require.that(layers.get(i - 1).version().family().compareTo(layers.get(i).version().family()) < 0,
                    "layers must be sorted by family order");
        }
    }

    /** @return the versions present, in layer order; never null */
    public Set<TagVersion> versions() {
        Set<TagVersion> versions = EnumSet.noneOf(TagVersion.class);
        layers.forEach(layer -> versions.add(layer.version()));
        return versions;
    }

    /**
     * @param family family to look up; not null
     * @return the layer of that family, empty if the file has none
     * @throws NullPointerException if {@code family} is null
     */
    public Optional<TagLayer> layer(TagFamily family) {
        Require.nonNull(family, "family");
        return layers.stream().filter(layer -> layer.version().family() == family).findFirst();
    }

    /** @return per field the value of the first layer (by precedence) that has it: v2 primary, v1 fallback */
    public MediaMetadata effective() {
        Map<Field, FieldValue> merged = new EnumMap<>(Field.class);
        for (TagLayer layer : layers) {
            layer.metadata().values().forEach(merged::putIfAbsent);
        }
        return new MediaMetadata(merged);
    }

    /**
     * Fields present in both the ID3v1 and the ID3v2 layer whose values disagree.
     *
     * <p>LENIENT: not a conflict if equal, or if the v1 value equals the v2 value after transliteration/truncation
     * for ID3v1, or if the v2 genre has no ID3v1 number. For tracks only the number is compared, because ID3v1 has no
     * total. STRICT: every inequality is a conflict (except for the track total, which v1 cannot store).
     *
     * @param mode comparison mode; not null
     * @return the conflicting fields in field order; empty if the file does not hold both tags
     * @throws NullPointerException if {@code mode} is null
     */
    public Set<Field> conflicts(V1Comparison mode) {
        Require.nonNull(mode, "mode");
        Optional<TagLayer> v1 = layer(TagFamily.ID3V1);
        Optional<TagLayer> v2 = layer(TagFamily.ID3V2);
        Set<Field> conflicts = EnumSet.noneOf(Field.class);
        if (v1.isEmpty() || v2.isEmpty()) {
            return conflicts;
        }
        for (Field field : Field.values()) {
            Optional<FieldValue> one = v1.get().metadata().get(field);
            Optional<FieldValue> two = v2.get().metadata().get(field);
            if (one.isPresent() && two.isPresent()
                    && differs(field, one.get(), two.get(), v1.get().version(), mode)) {
                conflicts.add(field);
            }
        }
        return conflicts;
    }

    private static boolean differs(Field field, FieldValue v1, FieldValue v2, TagVersion v1Version,
                                   V1Comparison mode) {
        if (v1.equals(v2)) {
            return false;
        }
        if (field == Field.TRACK) {
            return ((FieldValue.Position) v1).number() != ((FieldValue.Position) v2).number();
        }
        if (mode == V1Comparison.STRICT) {
            return true;
        }
        if (v1 instanceof FieldValue.Text one && v2 instanceof FieldValue.Text two) {
            int bytes = field == Field.COMMENT && v1Version == TagVersion.ID3V1_1 ? V1_1_COMMENT_BYTES : V1_TEXT_BYTES;
            return !isV1Projection(one.value(), two.value(), bytes);
        }
        if (v1 instanceof FieldValue.Genres one && v2 instanceof FieldValue.Genres two) {
            if (Id3v1Genres.number(two.primary().name()).isEmpty()) {
                return false; // v2 genre has no ID3v1 number, v1 can only hold "Other" or nothing
            }
            return !one.primary().name().equalsIgnoreCase(two.primary().name());
        }
        return true;
    }

    /** True if {@code v1} is what ID3v1 would hold for {@code v2}: transliterated (ASCII or Latin-1) and cut. */
    private static boolean isV1Projection(String v1, String v2, int bytes) {
        for (boolean ascii : new boolean[] {true, false}) {
            Id3v1Text.FittedText fitted = Id3v1Text.fit(v2, ascii, bytes);
            if (fitted.text().equals(v1)) {
                return true;
            }
            String converted = ascii
                    ? Transliterator.toAscii(v2).text()
                    : Transliterator.toLatin1(v2).text();
            converted = converted.strip();
            if (converted.length() > bytes && converted.substring(0, bytes).stripTrailing().equals(v1)) {
                return true; // hard cut by another tool
            }
        }
        return false;
    }
}
