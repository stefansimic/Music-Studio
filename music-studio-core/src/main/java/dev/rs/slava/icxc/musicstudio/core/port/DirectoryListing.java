package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of {@link MediaFileSource#list}.
 *
 * @param root    the listed directory, absolute
 * @param files   regular files, absolute, unique, no nulls
 * @param skipped deliberately skipped entries, absolute, unique, disjoint from {@code files}
 * @param issues  problems found while listing; no nulls
 */
public record DirectoryListing(Path root, List<Path> files, List<SkippedEntry> skipped, List<Issue> issues) {
    public DirectoryListing {
        Require.absolute(root, "root");
        Require.noNullElements(files, "files");
        Require.noNullElements(skipped, "skipped");
        Require.noNullElements(issues, "issues");
        files = List.copyOf(files);
        skipped = List.copyOf(skipped);
        issues = List.copyOf(issues);
        Set<Path> seen = new HashSet<>(files.size() * 2);
        for (Path file : files) {
            Require.absolute(file, "files element");
            Require.that(seen.add(file), "duplicate file in listing: %s", file);
        }
        Set<Path> skippedPaths = new HashSet<>();
        for (SkippedEntry entry : skipped) {
            Require.that(skippedPaths.add(entry.path()), "duplicate skipped entry: %s", entry.path());
            Require.that(!seen.contains(entry.path()), "entry is both listed and skipped: %s", entry.path());
        }
    }
}
