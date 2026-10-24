package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

/**
 * A file whose tags were read (possibly an untagged file with empty layers).
 *
 * @param path        absolute path; not null
 * @param format      the file's format; not null, equal to {@code tags.format()}
 * @param fingerprint size and modification time at scan time; not null
 * @param tags        what was read; not null
 * @param conflicts   fields where ID3v1 and ID3v2 differ; a subset of the fields present in both layers; copied
 */
public record ScannedFile(Path path, MediaFormat format, FileFingerprint fingerprint, TagReadResult tags,
                          Set<Field> conflicts) implements ScanItem {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if {@code path} is relative, the formats differ, or a conflict field is not
     *                                  present in both ID3 layers
     */
    public ScannedFile {
        Require.absolute(path, "path");
        Require.nonNull(format, "format");
        Require.nonNull(fingerprint, "fingerprint");
        Require.nonNull(tags, "tags");
        Require.noNullElements(conflicts, "conflicts");
        Require.that(tags.format() == format, "format %s does not match tags format %s", format, tags.format());
        for (Field field : conflicts) {
            Require.that(present(tags, TagFamily.ID3V1, field) && present(tags, TagFamily.ID3V2, field),
                    "conflict field %s is not present in both layers", field);
        }
        conflicts = conflicts.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(conflicts));
    }

    private static boolean present(TagReadResult tags, TagFamily family, Field field) {
        return tags.layer(family).flatMap(layer -> layer.metadata().get(field)).isPresent();
    }
}
