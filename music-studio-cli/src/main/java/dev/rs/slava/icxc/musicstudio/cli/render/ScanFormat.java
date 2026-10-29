package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.Set;
import java.util.stream.Collectors;

/** Wording shared by the renderers. */
final class ScanFormat {

    private ScanFormat() {}

    /** @return the tag versions present, for example "ID3v1.1 + ID3v2.3", or "none" */
    static String tags(TagReadResult tags) {
        Set<TagVersion> versions = tags.versions();
        return versions.isEmpty() ? "none" : versions.stream().map(TagVersion::label).collect(Collectors.joining(" + "));
    }

    /** @return "yes" or "no": whether the file has an embedded picture */
    static String cover(TagReadResult tags) {
        return tags.cover().isPresent() ? "yes" : "no";
    }

    /** @return the pattern names of the conflicting fields, comma separated, for example "artist,title" */
    static String differs(Set<Field> conflicts) {
        return java.util.Arrays.stream(Field.values()).filter(conflicts::contains).map(Field::placeholder)
                .collect(Collectors.joining(","));
    }
}
