package dev.rs.slava.icxc.musicstudio.core.port;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;

/**
 * Expected failure of a media or file operation at a port (missing file, no permission, corrupt content, I/O error).
 * Adapters convert every library or I/O exception into this type; none of those may escape a port.
 */
public final class MediaAccessException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why the operation failed. */
    public enum Reason {
        NOT_FOUND,
        NOT_A_DIRECTORY,
        ACCESS_DENIED,
        CORRUPT,
        UNSUPPORTED_FORMAT,
        ALREADY_EXISTS,
        NO_SPACE,
        FILE_TOO_LARGE,
        IO
    }

    private final Reason reason;
    private final transient Path path;

    /**
     * @param reason  not null
     * @param path    the affected file or directory, absolute; not null
     * @param message human-readable, not blank
     * @param cause   the underlying exception; may be null
     */
    public MediaAccessException(Reason reason, Path path, String message, Throwable cause) {
        super(Require.nonBlank(message, "message"), cause);
        this.reason = Require.nonNull(reason, "reason");
        this.path = Require.absolute(path, "path");
    }

    /** @return the failure reason; never null */
    public Reason reason() {
        return reason;
    }

    /** @return the affected path, absolute; never null */
    public Path path() {
        return path;
    }
}
