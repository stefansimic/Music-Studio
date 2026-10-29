package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Manual edits per file and the ambiguous matches the user accepted. Edits always win over everything else.
 *
 * @param edits             per file the edited fields; not null, no nulls
 * @param acceptedAmbiguous files whose ambiguous match is accepted; not null
 */
public record ManualEdits(Map<Path, Map<Field, FieldEdit>> edits, Set<Path> acceptedAmbiguous) {

    /** No edits. */
    public static final ManualEdits NONE = new ManualEdits(Map.of(), Set.of());

    public ManualEdits {
        Require.noNullEntries(edits, "edits");
        Require.noNullElements(acceptedAmbiguous, "acceptedAmbiguous");
        edits.forEach((path, fields) -> {
            Require.absolute(path, "edited path");
            Require.noNullEntries(fields, "edits of " + path);
            fields.forEach((field, edit) -> {
                if (edit instanceof FieldEdit.Set set) {
                    Require.that(set.value().type() == field.type(), "value type %s does not match %s",
                            set.value().type(), field);
                }
            });
        });
        acceptedAmbiguous.forEach(path -> Require.absolute(path, "accepted path"));
        edits = Map.copyOf(edits);
        acceptedAmbiguous = Set.copyOf(acceptedAmbiguous);
    }
}
