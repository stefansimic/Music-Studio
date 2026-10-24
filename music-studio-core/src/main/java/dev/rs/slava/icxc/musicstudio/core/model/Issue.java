package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.util.Optional;

/**
 * An expected problem reported in a result (never thrown).
 *
 * @param severity how serious; not null
 * @param code     stable code; not null
 * @param message  human-readable English; not blank
 * @param file     the affected file; not null, absolute if present
 * @param field    the affected field; not null
 */
public record Issue(Severity severity, IssueCode code, String message, Optional<Path> file, Optional<Field> field) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if {@code message} is blank or {@code file} is relative
     */
    public Issue {
        Require.nonNull(severity, "severity");
        Require.nonNull(code, "code");
        Require.nonBlank(message, "message");
        Require.nonNull(file, "file");
        file.ifPresent(f -> Require.absolute(f, "file"));
        Require.nonNull(field, "field");
    }

    /**
     * @param code    not null
     * @param message not blank
     * @return an ERROR issue without file and field
     */
    public static Issue error(IssueCode code, String message) {
        return new Issue(Severity.ERROR, code, message, Optional.empty(), Optional.empty());
    }

    /**
     * @param code    not null
     * @param message not blank
     * @return a WARNING issue without file and field
     */
    public static Issue warning(IssueCode code, String message) {
        return new Issue(Severity.WARNING, code, message, Optional.empty(), Optional.empty());
    }

    /**
     * @param code    not null
     * @param message not blank
     * @return an INFO issue without file and field
     */
    public static Issue info(IssueCode code, String message) {
        return new Issue(Severity.INFO, code, message, Optional.empty(), Optional.empty());
    }

    /**
     * @param path the affected file; not null, absolute
     * @return a copy with {@code file} set
     */
    public Issue forFile(Path path) {
        Require.absolute(path, "path");
        return new Issue(severity, code, message, Optional.of(path), field);
    }

    /**
     * @param affected the affected field; not null
     * @return a copy with {@code field} set
     */
    public Issue forField(Field affected) {
        Require.nonNull(affected, "affected");
        return new Issue(severity, code, message, file, Optional.of(affected));
    }
}
