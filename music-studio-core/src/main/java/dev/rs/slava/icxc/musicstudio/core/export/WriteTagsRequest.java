package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.List;

/**
 * What to write.
 *
 * @param items   the files to write; not null, no nulls (may be empty)
 * @param options the tag write options for all items; not null
 */
public record WriteTagsRequest(List<WriteItem> items, TagWriteOptions options) {

    public WriteTagsRequest {
        Require.noNullElements(items, "items");
        Require.nonNull(options, "options");
        items = List.copyOf(items);
    }
}
