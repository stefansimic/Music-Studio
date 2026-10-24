package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * {@link MediaFileSource} on the local file system.
 *
 * <p>Walks with {@link Files#walkFileTree} without {@code FOLLOW_LINKS}, so directory symlinks are never followed
 * (no loops, no escaping the tree). Hidden means: name starts with "." or, on Windows, the hidden attribute is set.
 * Read-only: nothing is created, modified or deleted.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class LocalMediaFileSource implements MediaFileSource {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Creates the source. */
    public LocalMediaFileSource() {
        // stateless
    }

    @Override
    public DirectoryListing list(Path root, ListingOptions options, Cancellation cancel) throws MediaAccessException {
        Require.absolute(root, "root");
        Require.nonNull(options, "options");
        Require.nonNull(cancel, "cancel");
        cancel.throwIfCancelled();
        Path normalized = root.normalize();
        checkRoot(normalized);

        // A symlinked root is walked through its target; reported paths keep the root the caller gave.
        Path walkRoot = normalized;
        if (Files.isSymbolicLink(normalized)) {
            try {
                walkRoot = normalized.toRealPath();
            } catch (IOException e) {
                throw failure(normalized, e);
            }
        }
        Walker walker = new Walker(normalized, walkRoot, options, cancel);
        try {
            Files.walkFileTree(walkRoot, EnumSet.noneOf(FileVisitOption.class),
                    options.recursive() ? Integer.MAX_VALUE : 1, walker);
        } catch (RootFailure e) {
            throw failure(normalized, e.io);
        } catch (IOException e) {
            throw failure(normalized, e);
        }
        return new DirectoryListing(normalized, walker.files, walker.skipped, walker.issues);
    }

    private static void checkRoot(Path root) throws MediaAccessException {
        if (!Files.exists(root)) {
            throw new MediaAccessException(Reason.NOT_FOUND, root, "folder not found: " + root, null);
        }
        if (!Files.isDirectory(root)) {
            throw new MediaAccessException(Reason.NOT_A_DIRECTORY, root, "not a folder: " + root, null);
        }
        if (!Files.isReadable(root)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, root, "cannot read folder: " + root, null);
        }
    }

    @Override
    public FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            throw failure(file, e);
        }
        if (!attributes.isRegularFile()) {
            throw new MediaAccessException(Reason.IO, file, "not a regular file: " + file, null);
        }
        return new FileFingerprint(attributes.size(), attributes.lastModifiedTime().toInstant());
    }

    private static MediaAccessException failure(Path path, IOException e) {
        Reason reason = switch (e) {
            case NoSuchFileException ignored -> Reason.NOT_FOUND;
            case AccessDeniedException ignored -> Reason.ACCESS_DENIED;
            default -> Reason.IO;
        };
        String message = switch (reason) {
            case NOT_FOUND -> "not found: " + path;
            case ACCESS_DENIED -> "access denied: " + path;
            default -> "cannot read " + path + ": " + e.getMessage();
        };
        return new MediaAccessException(reason, path, message, e);
    }

    private static boolean isHidden(Path path) {
        Path name = path.getFileName();
        if (name != null && name.toString().startsWith(".")) {
            return true;
        }
        if (WINDOWS) {
            try {
                return Files.isHidden(path);
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /** Wraps a failure of the root itself so it can leave the visitor. */
    private static final class RootFailure extends IOException {
        private static final long serialVersionUID = 1L;
        private final transient IOException io;

        RootFailure(IOException io) {
            super(io);
            this.io = io;
        }
    }

    private static final class Walker extends SimpleFileVisitor<Path> {
        private final Path root;
        private final Path walkRoot;
        private final ListingOptions options;
        private final Cancellation cancel;
        private final List<Path> files = new ArrayList<>();
        private final List<SkippedEntry> skipped = new ArrayList<>();
        private final List<Issue> issues = new ArrayList<>();

        Walker(Path root, Path walkRoot, ListingOptions options, Cancellation cancel) {
            this.root = root;
            this.walkRoot = walkRoot;
            this.options = options;
            this.cancel = cancel;
        }

        /** Maps a walked path back below the root the caller gave. */
        private Path reported(Path walked) {
            return walkRoot.equals(root) ? walked : root.resolve(walkRoot.relativize(walked));
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            cancel.throwIfCancelled();
            if (dir.equals(walkRoot)) {
                return FileVisitResult.CONTINUE;
            }
            return skipDirectory(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        /** Records hidden/excluded directories; true if the directory must not be descended into. */
        private boolean skipDirectory(Path dir) {
            Path path = reported(dir);
            if (!options.includeHidden() && isHidden(dir)) {
                skipped.add(new SkippedEntry(path, SkipReason.HIDDEN));
                return true;
            }
            if (options.excludedDirectories().contains(path) || options.excludedDirectories().contains(dir)) {
                skipped.add(new SkippedEntry(path, SkipReason.EXCLUDED));
                return true;
            }
            return false;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            boolean directory = attrs.isDirectory() || attrs.isSymbolicLink() && Files.isDirectory(file);
            if (directory) {
                // depth limit (flat listing) reports directories here; links to directories are never followed
                if (options.recursive()) {
                    if (attrs.isSymbolicLink()) {
                        if (!options.includeHidden() && isHidden(file)) {
                            skipped.add(new SkippedEntry(reported(file), SkipReason.HIDDEN));
                        } else {
                            skipped.add(new SkippedEntry(reported(file), SkipReason.DIRECTORY_SYMLINK));
                        }
                    } else {
                        skipDirectory(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
            boolean regular = attrs.isRegularFile() || attrs.isSymbolicLink() && Files.isRegularFile(file);
            boolean broken = attrs.isSymbolicLink() && !Files.exists(file);
            if (!regular && !broken) {
                return FileVisitResult.CONTINUE; // devices, pipes, sockets
            }
            if (!options.includeHidden() && isHidden(file)) {
                skipped.add(new SkippedEntry(reported(file), SkipReason.HIDDEN));
            } else if (broken) {
                skipped.add(new SkippedEntry(reported(file), SkipReason.BROKEN_SYMLINK));
            } else {
                files.add(reported(file));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            if (file.equals(walkRoot)) {
                throw new RootFailure(exc);
            }
            Path path = reported(file);
            if (!options.recursive() && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                return FileVisitResult.CONTINUE;
            }
            if (!options.includeHidden() && isHidden(file)) {
                skipped.add(new SkippedEntry(path, SkipReason.HIDDEN));
            } else if (options.excludedDirectories().contains(path)) {
                skipped.add(new SkippedEntry(path, SkipReason.EXCLUDED));
            } else {
                String what = Files.isDirectory(file) ? "folder" : "file";
                issues.add(Issue.warning(IssueCode.LISTING_ACCESS_DENIED,
                        "cannot read " + what + " " + path + ": " + describe(exc)).forFile(path));
            }
            return FileVisitResult.CONTINUE;
        }

        private static String describe(IOException e) {
            return e instanceof AccessDeniedException ? "access denied" : String.valueOf(e.getMessage());
        }
    }
}
