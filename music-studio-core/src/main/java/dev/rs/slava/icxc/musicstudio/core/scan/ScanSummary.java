package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Totals of a scan.
 *
 * @param root              the scanned folder, absolute
 * @param status            how the scan ended
 * @param found             files whose format was selected by the request
 * @param read              files delivered as {@link ScannedFile}
 * @param failed            files delivered as {@link UnreadableFile}; {@code found == read + failed} if COMPLETED
 * @param ignoredFiles      files with another extension, which were not read
 * @param ignoredByExtension {@code ignoredFiles} per lower-case extension ("(none)" for files without one)
 * @param skipped           hidden entries, directory symlinks and excluded folders that were not scanned, sorted
 * @param issues            problems of the listing (unreadable subfolders) or the reason for FAILED
 */
public record ScanSummary(Path root, ScanStatus status, int found, int read, int failed, int ignoredFiles,
                          Map<String, Integer> ignoredByExtension, List<SkippedEntry> skipped, List<Issue> issues) {

    /** Key used in {@code ignoredByExtension} for files without extension. */
    public static final String NO_EXTENSION = "(none)";

    public ScanSummary {
        Require.absolute(root, "root");
        Require.nonNull(status, "status");
        Require.that(found >= 0 && read >= 0 && failed >= 0 && ignoredFiles >= 0, "counters must not be negative");
        Require.noNullEntries(ignoredByExtension, "ignoredByExtension");
        Require.noNullElements(skipped, "skipped");
        Require.noNullElements(issues, "issues");
        Require.that(read + failed <= found, "read + failed must not exceed found");
        Require.that(status != ScanStatus.COMPLETED || found == read + failed,
                "a completed scan must account for every found file (found=%d read=%d failed=%d)", found, read, failed);
        Require.that(status != ScanStatus.FAILED || found == 0, "a failed scan has no files");
        Require.that(ignoredByExtension.values().stream().mapToInt(Integer::intValue).sum() == ignoredFiles,
                "ignoredByExtension must add up to ignoredFiles");
        ignoredByExtension = java.util.Collections.unmodifiableMap(new TreeMap<>(ignoredByExtension));
        skipped = List.copyOf(skipped);
        issues = List.copyOf(issues);
    }
}
