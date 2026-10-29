package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Set;

/**
 * Options of {@link MediaFileSource#list}.
 *
 * @param recursive           descend into subdirectories
 * @param includeHidden       list hidden files and directories instead of skipping them
 * @param excludedDirectories absolute paths, no nulls; these directories and their content are not listed
 */
public record ListingOptions(boolean recursive, boolean includeHidden, Set<Path> excludedDirectories) {
    public ListingOptions {
        Require.noNullElements(excludedDirectories, "excludedDirectories");
        excludedDirectories = Set.copyOf(excludedDirectories);
        excludedDirectories.forEach(dir -> Require.absolute(dir, "excludedDirectories element"));
    }

    /** @return flat listing without hidden entries and without exclusions */
    public static ListingOptions defaults() {
        return new ListingOptions(false, false, Set.of());
    }

    /** @return a copy with the given recursion flag */
    public ListingOptions withRecursive(boolean value) {
        return new ListingOptions(value, includeHidden, excludedDirectories);
    }

    /** @return a copy with the given hidden flag */
    public ListingOptions withIncludeHidden(boolean value) {
        return new ListingOptions(recursive, value, excludedDirectories);
    }

    /** @return a copy with the given excluded directories; absolute, no nulls */
    public ListingOptions withExcludedDirectories(Set<Path> value) {
        return new ListingOptions(recursive, includeHidden, value);
    }
}
