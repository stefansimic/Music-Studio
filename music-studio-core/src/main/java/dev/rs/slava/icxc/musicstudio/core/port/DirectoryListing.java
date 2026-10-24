package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The result of {@link MediaFileSource#list}.
 *
 * @param root    the listed directory; absolute, not null
 * @param files   regular files found; absolute, below {@code root}, unique, no nulls; order unspecified; copied
 * @param skipped entries deliberately not listed; absolute, unique, disjoint from {@code files}; copied
 * @param issues  problems such as unreadable subdirectories; no nulls; copied
 */
public record DirectoryListing(Path root, List<Path> files, List<SkippedEntry> skipped, List<Issue> issues) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or an element is null
     * @throws IllegalArgumentException if a path is relative or outside root, or the uniqueness/disjointness rules
     *                                  are violated
     */
    public DirectoryListing {
        Require.absolute(root, "root");
        Require.noNullElements(files, "files");
        Require.noNullElements(skipped, "skipped");
        Require.noNullElements(issues, "issues");
        Set<Path> seen = new HashSet<>();
        for (Path file : files) {
            Require.absolute(file, "files element");
            Require.that(file.startsWith(root) && !file.equals(root), "file %s is not below root %s", file, root);
            Require.that(seen.add(file), "duplicate file %s", file);
        }
        Set<Path> seenSkipped = new HashSet<>();
        for (SkippedEntry entry : skipped) {
            Require.that(seenSkipped.add(entry.path()), "duplicate skipped entry %s", entry.path());
            Require.that(!seen.contains(entry.path()), "%s is both listed and skipped", entry.path());
        }
        files = List.copyOf(files);
        skipped = List.copyOf(skipped);
        issues = List.copyOf(issues);
    }
}
