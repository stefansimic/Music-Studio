package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediaFileSource} on the local file system. Strictly read-only. Symbolic links to directories are never
 * followed, so a link loop or a link out of the tree cannot be walked into.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class LocalMediaFileSource implements MediaFileSource {

    @Override
    public DirectoryListing list(Path root, ListingOptions options, Cancellation cancel) throws MediaAccessException {
        Require.absolute(root, "root");
        Require.nonNull(options, "options");
        Require.nonNull(cancel, "cancel");
        cancel.throwIfCancelled();
        Path base = root.normalize();
        requireReadableDirectory(base);

        List<Path> files = new ArrayList<>();
        List<SkippedEntry> skipped = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        walk(base, options, cancel, files, skipped, issues);
        return new DirectoryListing(base, files, skipped, issues);
    }

    private static void requireReadableDirectory(Path dir) throws MediaAccessException {
        // opening the stream fails for missing, non-directory and unreadable directories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            assert stream.iterator() != null;
        } catch (NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, dir, "no such directory: " + dir, e);
        } catch (NotDirectoryException e) {
            throw new MediaAccessException(Reason.NOT_A_DIRECTORY, dir, "not a directory: " + dir, e);
        } catch (AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, dir, "cannot read directory: " + dir, e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, dir, "cannot read directory " + dir + ": " + e.getMessage(), e);
        }
    }

    private static void walk(Path dir, ListingOptions options, Cancellation cancel, List<Path> files,
                             List<SkippedEntry> skipped, List<Issue> issues) {
        cancel.throwIfCancelled();
        List<Path> subdirectories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (!options.includeHidden() && isHidden(child)) {
                    skipped.add(new SkippedEntry(child, SkipReason.HIDDEN));
                    continue;
                }
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException e) {
                    issues.add(Issue.warning(IssueCode.LISTING_ACCESS_DENIED, "cannot read " + child).forFile(child));
                    continue;
                }
                if (attributes.isSymbolicLink()) {
                    if (Files.isDirectory(child)) {
                        skipped.add(new SkippedEntry(child, SkipReason.DIRECTORY_SYMLINK));
                    } else if (Files.isRegularFile(child)) {
                        files.add(child);
                    } // a dangling link is not a regular file
                } else if (attributes.isDirectory()) {
                    if (options.recursive()) {
                        if (options.excludedDirectories().contains(child)) {
                            skipped.add(new SkippedEntry(child, SkipReason.EXCLUDED));
                        } else {
                            subdirectories.add(child);
                        }
                    }
                } else if (attributes.isRegularFile()) {
                    files.add(child);
                }
            }
        } catch (IOException | DirectoryIteratorException e) {
            issues.add(Issue.warning(IssueCode.LISTING_ACCESS_DENIED, "cannot read directory " + dir).forFile(dir));
            return;
        }
        for (Path subdirectory : subdirectories) {
            walk(subdirectory, options, cancel, files, skipped, issues);
        }
    }

    private static boolean isHidden(Path path) {
        if (path.getFileName().toString().startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(path); // DOS hidden attribute on Windows
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileFingerprint(attributes.size(), attributes.lastModifiedTime().toInstant());
        } catch (NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "no such file: " + file, e);
        } catch (AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied: " + file, e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, file, "cannot read " + file + ": " + e.getMessage(), e);
        }
    }
}
