package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link ExportTarget} for the local filesystem. Never replaces an existing file: the final move first tries a hard
 * link (atomic and fails if the name exists), and on filesystems without hard links (FAT/exFAT sticks) falls back to
 * check-then-rename, which leaves a tiny race that is acceptable because nothing else writes into an export folder.
 * Deletes only files whose name contains {@link ExportTarget#TEMP_MARKER}.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class LocalExportTarget implements ExportTarget {

    private static final int MAX_STEM_BYTES = 100;
    private static final int MAX_EXTENSION_BYTES = 20;

    @Override
    public boolean exists(Path path) {
        Require.absolute(path, "path");
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public Path reserveTempPath(Path target) {
        Require.absolute(target, "target");
        Path directory = target.getParent();
        Require.that(directory != null, "target needs a parent directory");
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        stem = cutBytes(stem, MAX_STEM_BYTES);
        extension = extension.getBytes(StandardCharsets.UTF_8).length > MAX_EXTENSION_BYTES ? "" : extension;
        while (true) {
            String random = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFL);
            Path candidate = directory.resolve("." + stem + "." + random + TEMP_MARKER + extension);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
    }

    /** Shortens {@code text} to at most {@code maxBytes} UTF-8 bytes without splitting a character. */
    private static String cutBytes(String text, int maxBytes) {
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) {
                break;
            }
            out.appendCodePoint(cp);
            bytes += size;
        }
        return out.toString();
    }

    @Override
    public void moveNoReplace(Path temp, Path target) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.absolute(target, "target");
        Require.that(temp.getFileName().toString().contains(TEMP_MARKER), "temp must be a temp file of this port");
        if (!Files.isRegularFile(temp, LinkOption.NOFOLLOW_LINKS)) {
            throw new MediaAccessException(Reason.NOT_FOUND, temp, "The temp file does not exist", null);
        }
        try {
            try {
                Files.createLink(target, temp);
            } catch (FileAlreadyExistsException e) {
                throw alreadyExists(target, e);
            } catch (UnsupportedOperationException e) {
                renameWithoutReplace(temp, target); // no hard links on this filesystem
                return;
            } catch (AccessDeniedException e) {
                throw e;
            } catch (FileSystemException e) {
                if (noHardLinks(e)) { // FAT/exFAT: "Operation not permitted" / "not supported"
                    renameWithoutReplace(temp, target);
                    return;
                }
                throw e;
            }
        } catch (FileAlreadyExistsException e) {
            throw alreadyExists(target, e);
        } catch (IOException e) {
            throw map(target, e);
        }
        try {
            Files.delete(temp);
        } catch (IOException e) {
            // the target is complete; the caller removes the leftover temp name with deleteTemp
        }
    }

    private static boolean noHardLinks(FileSystemException e) {
        String reason = e.getReason() == null ? "" : e.getReason().toLowerCase();
        return reason.contains("not permitted") || reason.contains("not supported")
                || reason.contains("not implemented");
    }

    private static void renameWithoutReplace(Path temp, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        Files.move(temp, target); // without REPLACE_EXISTING: fails if the target appeared meanwhile
    }

    @Override
    public void deleteTemp(Path temp) throws MediaAccessException {
        Require.absolute(temp, "temp");
        String name = temp.getFileName().toString();
        Require.that(name.startsWith(".") && name.contains(TEMP_MARKER), "only temp files can be deleted");
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            throw map(temp, e);
        }
    }

    private static MediaAccessException alreadyExists(Path target, IOException cause) {
        return new MediaAccessException(Reason.ALREADY_EXISTS, target, "The target already exists", cause);
    }

    private static boolean isNoSpace(FileSystemException e) {
        String reason = e.getReason();
        return reason != null && reason.toLowerCase().contains("no space");
    }

    private static MediaAccessException map(Path path, IOException e) {
        if (e instanceof NoSuchFileException) {
            return new MediaAccessException(Reason.NOT_FOUND, path, "The file or folder does not exist", e);
        }
        if (e instanceof AccessDeniedException) {
            return new MediaAccessException(Reason.ACCESS_DENIED, path, "Access denied", e);
        }
        if (e instanceof FileSystemException fse && isNoSpace(fse)) {
            return new MediaAccessException(Reason.NO_SPACE, path, "No space left on the device", e);
        }
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new MediaAccessException(Reason.IO, path, detail, e);
    }
}
