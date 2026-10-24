package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.text.Id3v1Text;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a tag reader found in one file.
 *
 * <p>Invariants: at most one layer per {@link TagFamily}; layers sorted by {@link TagFamily} order; every layer's
 * version belongs to a family that {@code format} can hold (MP3: ID3V2, ID3V1).
 *
 * <p><b>Thread-safety:</b> immutable.
 *
 * @param format the file's format; not null
 * @param layers the tag layers found; not null, no null elements, may be empty (untagged file); copied
 * @param stream the stream properties, empty if the headers could not be parsed; not null
 * @param cover  the front cover, or the first picture with type OTHER, empty if none; not null
 * @param issues problems found while reading (damaged tag, invalid values); not null, no null elements; copied
 */
public record TagReadResult(MediaFormat format, List<TagLayer> layers, Optional<StreamInfo> stream,
                            Optional<CoverInfo> cover, List<Issue> issues) {

    /** Field size of ID3v1 title, artist, album and the v1.0 comment. */
    private static final int V1_TEXT_BYTES = 30;
    /** Field size of the ID3v1.1 comment (2 bytes are used for the track). */
    private static final int V1_1_COMMENT_BYTES = 28;

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if a family occurs twice, the layers are not sorted, or a version does not
     *                                  fit the format
     */
    public TagReadResult {
        Require.nonNull(format, "format");
        Require.noNullElements(layers, "layers");
        Require.nonNull(stream, "stream");
        Require.nonNull(cover, "cover");
        Require.noNullElements(issues, "issues");
        layers = List.copyOf(layers);
        issues = List.copyOf(issues);
        TagFamily previous = null;
        for (TagLayer layer : layers) {
            TagFamily family = layer.version().family();
            Require.that(format.tagFamilies().contains(family), "%s cannot hold a %s tag", format,
                    layer.version().label());
            Require.that(previous != family, "duplicate tag family %s", family);
            Require.that(previous == null || previous.compareTo(family) < 0,
                    "layers must be sorted by tag family order");
            previous = family;
        }
    }

    /**
     * @param format the file's format; not null
     * @return a result for an untagged file without stream info, cover and issues
     */
    public static TagReadResult untagged(MediaFormat format) {
        return new TagReadResult(format, List.of(), Optional.empty(), Optional.empty(), List.of());
    }

    /** @return the versions of all layers; unmodifiable, never null */
    public Set<TagVersion> versions() {
        EnumSet<TagVersion> versions = EnumSet.noneOf(TagVersion.class);
        layers.forEach(layer -> versions.add(layer.version()));
        return Collections.unmodifiableSet(versions);
    }

    /**
     * @param family the tag family; not null
     * @return the layer of that family, or empty if the file has none
     * @throws NullPointerException if {@code family} is null
     */
    public Optional<TagLayer> layer(TagFamily family) {
        Require.nonNull(family, "family");
        return layers.stream().filter(layer -> layer.version().family() == family).findFirst();
    }

    /**
     * Per field the value of the first layer (by precedence) that has it: "v2 primary, v1 as fallback".
     *
     * @return the effective metadata; never null
     */
    public MediaMetadata effective() {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        for (TagLayer layer : layers) {
            layer.metadata().values().forEach(values::putIfAbsent);
        }
        return new MediaMetadata(values);
    }

    /**
     * Fields where an ID3v1 value disagrees with the ID3v2 value. Only fields present in both layers are compared.
     *
     * <p>{@link V1Comparison#LENIENT}: not a conflict if the values are equal, or if the v1 value is what fitting
     * the v2 value into ID3v1 yields (transliteration to ASCII or Latin-1, truncation at a word boundary or hard at
     * the field size), or if v1 cannot hold the difference (track total; a v2 genre without ID3v1 number).
     * {@link V1Comparison#STRICT}: every inequality is a conflict.
     *
     * @param mode the comparison mode; not null
     * @return the conflicting fields, a subset of the fields present in both layers; unmodifiable, never null
     * @throws NullPointerException if {@code mode} is null
     */
    public Set<Field> conflicts(V1Comparison mode) {
        Require.nonNull(mode, "mode");
        Optional<TagLayer> v1 = layer(TagFamily.ID3V1);
        Optional<TagLayer> v2 = layer(TagFamily.ID3V2);
        if (v1.isEmpty() || v2.isEmpty()) {
            return Set.of();
        }
        EnumSet<Field> conflicts = EnumSet.noneOf(Field.class);
        int commentBytes = v1.get().version() == TagVersion.ID3V1_1 ? V1_1_COMMENT_BYTES : V1_TEXT_BYTES;
        for (Map.Entry<Field, FieldValue> entry : v1.get().metadata().values().entrySet()) {
            Field field = entry.getKey();
            Optional<FieldValue> other = v2.get().metadata().get(field);
            if (other.isEmpty() || entry.getValue().equals(other.get())) {
                continue;
            }
            int bytes = field == Field.COMMENT ? commentBytes : V1_TEXT_BYTES;
            if (mode == V1Comparison.STRICT || !representsInV1(entry.getValue(), other.get(), bytes)) {
                conflicts.add(field);
            }
        }
        return Collections.unmodifiableSet(conflicts);
    }

    private static boolean representsInV1(FieldValue v1, FieldValue v2, int bytes) {
        return switch (v1) {
            case FieldValue.Text t1 when v2 instanceof FieldValue.Text t2 -> isFitted(t1.value(), t2.value(), bytes);
            case FieldValue.Position p1 when v2 instanceof FieldValue.Position p2 -> p1.number() == p2.number();
            case FieldValue.Genres g1 when v2 instanceof FieldValue.Genres g2 ->
                    g1.primary().name().equalsIgnoreCase(g2.primary().name())
                            || Id3v1Genres.number(g2.primary().name()).isEmpty();
            default -> false;
        };
    }

    private static boolean isFitted(String v1, String v2, int bytes) {
        for (boolean ascii : new boolean[] {true, false}) {
            String fitted = Id3v1Text.fit(v2, ascii, bytes).text();
            String full = Id3v1Text.fit(v2, ascii, Integer.MAX_VALUE).text();
            String hardCut = full.length() > bytes ? full.substring(0, bytes).strip() : full;
            if (v1.equals(fitted) || v1.equals(hardCut)) {
                return true;
            }
        }
        return false;
    }
}
