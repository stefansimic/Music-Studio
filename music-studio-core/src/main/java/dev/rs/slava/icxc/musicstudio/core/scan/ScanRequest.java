package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import java.nio.file.Path;
import java.util.Set;

/**
 * What to scan.
 *
 * @param root                absolute folder; not null
 * @param recursive           descend into subfolders
 * @param includeHidden       scan hidden files and folders instead of skipping them
 * @param formats             formats to read; non-empty, no nulls
 * @param excludedDirectories absolute folders to leave out; no nulls
 * @param v1Comparison        how ID3v1 and ID3v2 values are compared for the conflict marker; not null
 */
public record ScanRequest(Path root, boolean recursive, boolean includeHidden, Set<MediaFormat> formats,
                          Set<Path> excludedDirectories, V1Comparison v1Comparison) {
    public ScanRequest {
        Require.absolute(root, "root");
        Require.noNullElements(formats, "formats");
        Require.that(!formats.isEmpty(), "formats must not be empty");
        Require.noNullElements(excludedDirectories, "excludedDirectories");
        Require.nonNull(v1Comparison, "v1Comparison");
        formats = Set.copyOf(formats);
        excludedDirectories = Set.copyOf(excludedDirectories);
        excludedDirectories.forEach(dir -> Require.absolute(dir, "excludedDirectories element"));
    }

    /**
     * @param root absolute folder; not null
     * @return a flat scan of MP3 files, hidden entries skipped, lenient v1 comparison
     */
    public static ScanRequest of(Path root) {
        return new ScanRequest(root, false, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.LENIENT);
    }

    /** @return a copy with the given recursion flag */
    public ScanRequest withRecursive(boolean value) {
        return new ScanRequest(root, value, includeHidden, formats, excludedDirectories, v1Comparison);
    }

    /** @return a copy with the given hidden flag */
    public ScanRequest withIncludeHidden(boolean value) {
        return new ScanRequest(root, recursive, value, formats, excludedDirectories, v1Comparison);
    }

    /** @return a copy with the given comparison mode; not null */
    public ScanRequest withV1Comparison(V1Comparison value) {
        return new ScanRequest(root, recursive, includeHidden, formats, excludedDirectories, value);
    }
}
