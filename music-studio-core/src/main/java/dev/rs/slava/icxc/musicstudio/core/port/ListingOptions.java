package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Options for {@link MediaFileSource#list}.
 *
 * @param recursive           also list subdirectories
 * @param includeHidden       also list hidden files and descend into hidden directories
 * @param excludedDirectories directories to skip with their content; absolute paths, no nulls; copied and
 *                            normalised (so "/m/../m/x" equals "/m/x")
 */
public record ListingOptions(boolean recursive, boolean includeHidden, Set<Path> excludedDirectories) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if {@code excludedDirectories} or an element is null
     * @throws IllegalArgumentException if an excluded directory is relative
     */
    public ListingOptions {
        Require.noNullElements(excludedDirectories, "excludedDirectories");
        excludedDirectories.forEach(dir -> Require.absolute(dir, "excludedDirectories element"));
        excludedDirectories = excludedDirectories.stream().map(Path::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** @return the defaults: not recursive, hidden entries skipped, nothing excluded */
    public static ListingOptions defaults() {
        return new ListingOptions(false, false, Set.of());
    }
}
