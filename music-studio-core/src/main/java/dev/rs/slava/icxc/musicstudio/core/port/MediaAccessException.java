package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;

/**
 * Expected failure of a media/file operation at a port. Services catch it and turn it into an issue.
 * Adapters wrap every library or I/O exception into this type; nothing else escapes a port except the
 * {@code Require} exceptions and {@code OperationCancelledException}.
 */
public final class MediaAccessException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why the operation failed. */
    public enum Reason {
        /** The file or directory does not exist. */
        NOT_FOUND,
        /** A directory was expected. */
        NOT_A_DIRECTORY,
        /** Missing permission. */
        ACCESS_DENIED,
        /** Not a parsable file of the expected format (incl. 0-byte files). */
        CORRUPT,
        /** The format is not supported. */
        UNSUPPORTED_FORMAT,
        /** The target already exists. */
        ALREADY_EXISTS,
        /** The file system is full. */
        NO_SPACE,
        /** The file is too large for the target file system. */
        FILE_TOO_LARGE,
        /** Any other I/O failure. */
        IO
    }

    private final Reason reason;
    private final Path path;

    /**
     * Creates the exception.
     *
     * @param reason  why it failed; not null
     * @param path    the affected file or directory; not null, absolute
     * @param message human-readable English; not blank
     * @param cause   the underlying exception; may be null
     * @throws NullPointerException     if {@code reason}, {@code path} or {@code message} is null
     * @throws IllegalArgumentException if {@code path} is relative or {@code message} is blank
     */
    public MediaAccessException(Reason reason, Path path, String message, Throwable cause) {
        super(Require.nonBlank(message, "message"), cause);
        this.reason = Require.nonNull(reason, "reason");
        this.path = Require.absolute(path, "path");
    }

    /** @return why the operation failed; never null */
    public Reason reason() {
        return reason;
    }

    /** @return the affected file or directory; absolute, never null */
    public Path path() {
        return path;
    }
}
