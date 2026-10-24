package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The outcome of a scan. Items were delivered to the sink; this holds only counters and global information.
 *
 * @param root               the scanned folder; absolute, not null
 * @param status             how the scan ended; not null
 * @param found              files of a requested format; ≥ 0
 * @param read               files delivered as {@link ScannedFile}; ≥ 0
 * @param failed             files delivered as {@link UnreadableFile}; ≥ 0
 * @param filesWithErrors    read files whose tags carry an ERROR issue (e.g. TAG_DAMAGED); 0 ≤ value ≤ read
 * @param ignoredFiles       files of other formats; ≥ 0, equal to the sum of {@code ignoredByExtension}
 * @param ignoredByExtension ignored files per lower-case extension ("" for none); no nulls; copied, sorted
 * @param skipped            hidden entries, directory symlinks, broken symlinks and excluded folders, sorted by
 *                           PathOrder; copied
 * @param issues             global problems (ROOT_NOT_FOUND, LISTING_ACCESS_DENIED); no nulls; copied
 */
public record ScanSummary(Path root, ScanStatus status, int found, int read, int failed, int filesWithErrors,
                          int ignoredFiles,
                          Map<String, Integer> ignoredByExtension, List<SkippedEntry> skipped, List<Issue> issues) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if a counter is negative, {@code read + failed > found},
     *                                  {@code filesWithErrors > read}, a completed scan does not account for every
     *                                  found file, or the ignored counts do not add up
     */
    public ScanSummary {
        Require.absolute(root, "root");
        Require.nonNull(status, "status");
        Require.notNegative(found, "found");
        Require.notNegative(read, "read");
        Require.notNegative(failed, "failed");
        Require.notNegative(filesWithErrors, "filesWithErrors");
        Require.that(filesWithErrors <= read, "filesWithErrors must be <= read");
        Require.notNegative(ignoredFiles, "ignoredFiles");
        Require.noNullEntries(ignoredByExtension, "ignoredByExtension");
        Require.noNullElements(skipped, "skipped");
        Require.noNullElements(issues, "issues");
        Require.that(read + failed <= found, "read + failed must be <= found");
        Require.that(status != ScanStatus.COMPLETED || read + failed == found,
                "a completed scan must account for every found file");
        Require.that(ignoredByExtension.values().stream().mapToInt(Integer::intValue).sum() == ignoredFiles,
                "ignoredByExtension must add up to ignoredFiles");
        ignoredByExtension = Map.copyOf(new TreeMap<>(ignoredByExtension));
        skipped = List.copyOf(skipped);
        issues = List.copyOf(issues);
    }
}
