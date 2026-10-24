package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How one tag slot of a file changes: the version before/after and the changed values. This is the dry-run's
 * "field, old → new" per tag (ID3v1 shows the transliterated/truncated text).
 *
 * @param family the slot; not null
 * @param before the version before; empty = no tag before; not null
 * @param after  the version after; empty = no tag after; not null
 * @param values the changed values, in Field order; no nulls; copied
 */
public record SlotChange(TagFamily family, Optional<TagVersion> before, Optional<TagVersion> after,
                         List<ValueChange> values) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or element is null
     * @throws IllegalArgumentException if a version does not belong to {@code family}
     */
    public SlotChange {
        Require.nonNull(family, "family");
        Require.nonNull(before, "before");
        Require.nonNull(after, "after");
        Require.noNullElements(values, "values");
        before.ifPresent(v -> Require.that(v.family() == family, "%s is not a %s version", v, family));
        after.ifPresent(v -> Require.that(v.family() == family, "%s is not a %s version", v, family));
        values = List.copyOf(values);
    }

    /** @return true if the slot is the same before and after */
    public boolean unchanged() {
        return before.equals(after) && values.isEmpty();
    }

    /**
     * The changes per slot, one entry per family present before or after, in TagFamily order.
     *
     * @param before the layers before; not null
     * @param after  the layers after; not null
     * @return the slot changes; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<SlotChange> between(List<TagLayer> before, List<TagLayer> after) {
        Require.noNullElements(before, "before");
        Require.noNullElements(after, "after");
        List<SlotChange> changes = new ArrayList<>();
        for (TagFamily family : TagFamily.values()) {
            Optional<TagLayer> old = find(before, family);
            Optional<TagLayer> now = find(after, family);
            if (old.isEmpty() && now.isEmpty()) {
                continue;
            }
            MediaMetadata oldValues = old.map(TagLayer::metadata).orElse(MediaMetadata.empty());
            MediaMetadata newValues = now.map(TagLayer::metadata).orElse(MediaMetadata.empty());
            List<ValueChange> values = new ArrayList<>();
            for (Field field : oldValues.differingFields(newValues)) {
                values.add(new ValueChange(field, oldValues.get(field), newValues.get(field)));
            }
            changes.add(new SlotChange(family, old.map(TagLayer::version), now.map(TagLayer::version), values));
        }
        return List.copyOf(changes);
    }

    private static Optional<TagLayer> find(List<TagLayer> layers, TagFamily family) {
        return layers.stream().filter(layer -> layer.version().family() == family).findFirst();
    }
}
