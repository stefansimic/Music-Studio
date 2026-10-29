package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A problem or note about an item or a whole operation.
 *
 * @param severity not null
 * @param code     not null
 * @param message  human-readable English, not blank
 * @param file     absolute path if present
 * @param field    the affected field if any
 */
public record Issue(Severity severity, IssueCode code, String message, Optional<Path> file, Optional<Field> field) {
    public Issue {
        Require.nonNull(severity, "severity");
        Require.nonNull(code, "code");
        Require.nonBlank(message, "message");
        Require.nonNull(file, "file");
        Require.nonNull(field, "field");
        file.ifPresent(f -> Require.absolute(f, "file"));
    }

    /** @return an ERROR issue without file or field */
    public static Issue error(IssueCode code, String message) {
        return new Issue(Severity.ERROR, code, message, Optional.empty(), Optional.empty());
    }

    /** @return a WARNING issue without file or field */
    public static Issue warning(IssueCode code, String message) {
        return new Issue(Severity.WARNING, code, message, Optional.empty(), Optional.empty());
    }

    /** @return an INFO issue without file or field */
    public static Issue info(IssueCode code, String message) {
        return new Issue(Severity.INFO, code, message, Optional.empty(), Optional.empty());
    }

    /**
     * @param path absolute file; not null
     * @return a copy that refers to {@code path}
     */
    public Issue forFile(Path path) {
        Require.absolute(path, "path");
        return new Issue(severity, code, message, Optional.of(path), field);
    }

    /**
     * @param affected the affected field; not null
     * @return a copy that refers to {@code affected}
     */
    public Issue forField(Field affected) {
        Require.nonNull(affected, "affected");
        return new Issue(severity, code, message, file, Optional.of(affected));
    }
}
