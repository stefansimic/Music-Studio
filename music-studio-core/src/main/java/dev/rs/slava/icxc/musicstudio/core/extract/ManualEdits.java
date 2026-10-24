package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-file manual edits (GUI cells) and manual acceptance of ambiguous matches. Entries for paths that are not in
 * the request are ignored.
 *
 * @param edits             per absolute path: field → edit; no nulls; an Assign value's type fits its field; copied
 * @param acceptedAmbiguous absolute paths whose ambiguous match the user accepted; no nulls; copied
 */
public record ManualEdits(Map<Path, Map<Field, FieldEdit>> edits, Set<Path> acceptedAmbiguous) {

    /** No edits, nothing accepted. */
    public static final ManualEdits NONE = new ManualEdits(Map.of(), Set.of());

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component, key or value is null
     * @throws IllegalArgumentException if a path is relative or an Assign value does not fit its field
     */
    public ManualEdits {
        Require.noNullEntries(edits, "edits");
        Require.noNullElements(acceptedAmbiguous, "acceptedAmbiguous");
        Map<Path, Map<Field, FieldEdit>> copy = new HashMap<>();
        for (Map.Entry<Path, Map<Field, FieldEdit>> entry : edits.entrySet()) {
            Require.absolute(entry.getKey(), "edits path");
            Require.noNullEntries(entry.getValue(), "edits of " + entry.getKey());
            EnumMap<Field, FieldEdit> fields = new EnumMap<>(Field.class);
            entry.getValue().forEach((field, edit) -> {
                if (edit instanceof FieldEdit.Assign assign) {
                    Require.that(assign.value().type() == field.type(), "value of type %s does not fit %s",
                            assign.value().type(), field);
                }
                fields.put(field, edit);
            });
            copy.put(entry.getKey(), Collections.unmodifiableMap(fields));
        }
        acceptedAmbiguous.forEach(path -> Require.absolute(path, "acceptedAmbiguous path"));
        edits = Map.copyOf(copy);
        acceptedAmbiguous = Set.copyOf(acceptedAmbiguous);
    }

    /**
     * @param file an absolute path; not null
     * @return the edits of that file; empty if none; never null
     */
    public Map<Field, FieldEdit> of(Path file) {
        Require.nonNull(file, "file");
        return edits.getOrDefault(file, Map.of());
    }
}
