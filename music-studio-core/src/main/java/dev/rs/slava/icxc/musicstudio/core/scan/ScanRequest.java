package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import java.nio.file.Path;
import java.util.Set;

/**
 * What to scan.
 *
 * @param root                the folder to scan; absolute, not null
 * @param recursive           also scan subfolders
 * @param includeHidden       also scan hidden files and folders (name starts with ".")
 * @param formats             the formats to read; non-empty, no nulls; copied
 * @param excludedDirectories folders to skip; absolute, no nulls; copied
 * @param v1Comparison        how ID3v1/ID3v2 differences are detected; not null
 */
public record ScanRequest(Path root, boolean recursive, boolean includeHidden, Set<MediaFormat> formats,
                          Set<Path> excludedDirectories, V1Comparison v1Comparison) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if {@code root} or an excluded directory is relative, or {@code formats} is
     *                                  empty
     */
    public ScanRequest {
        Require.absolute(root, "root");
        Require.noNullElements(formats, "formats");
        Require.that(!formats.isEmpty(), "formats must not be empty");
        Require.noNullElements(excludedDirectories, "excludedDirectories");
        excludedDirectories.forEach(dir -> Require.absolute(dir, "excludedDirectories element"));
        Require.nonNull(v1Comparison, "v1Comparison");
        formats = Set.copyOf(formats);
        excludedDirectories = Set.copyOf(excludedDirectories);
    }

    /**
     * A request with the defaults: formats {MP3}, hidden entries skipped, nothing excluded, LENIENT comparison.
     *
     * @param root      absolute folder; not null
     * @param recursive also scan subfolders
     * @return the request
     */
    public static ScanRequest of(Path root, boolean recursive) {
        return new ScanRequest(root, recursive, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.LENIENT);
    }
}
