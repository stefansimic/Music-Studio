package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;

/** Maps every exception jaudiotagger can throw to a {@link MediaAccessException}; nothing else escapes. */
final class LibraryExceptions {

    private LibraryExceptions() {
    }

    /** Maps a failure while composing {@code target} from {@code source}. */
    static MediaAccessException mapWrite(Path source, Path target, IOException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("No space left") || message.contains("not enough space")
                || message.contains("Disk quota exceeded")) {
            return new MediaAccessException(Reason.NO_SPACE, target, "the disk is full", e);
        }
        if (e instanceof AccessDeniedException denied) {
            Path path = denied.getFile() != null && denied.getFile().equals(source.toString()) ? source : target;
            return new MediaAccessException(Reason.ACCESS_DENIED, path, "access denied", e);
        }
        if (e instanceof NoSuchFileException) {
            return new MediaAccessException(Reason.NOT_FOUND, source, "file not found", e);
        }
        return new MediaAccessException(Reason.IO, target, "write error (" + message + ")", e);
    }

    static MediaAccessException map(Path file, Throwable e) {
        Reason reason = switch (e) {
            case InvalidAudioFrameException ignored -> Reason.CORRUPT;
            case CannotReadException ignored -> Reason.CORRUPT;
            case TagException ignored -> Reason.CORRUPT;
            case ReadOnlyFileException ignored -> Reason.ACCESS_DENIED;
            case FileNotFoundException ignored -> Reason.NOT_FOUND;
            case NoSuchFileException ignored -> Reason.NOT_FOUND;
            case AccessDeniedException ignored -> Reason.ACCESS_DENIED;
            case IOException ignored -> Reason.IO;
            // a library bug or a parser tripping over garbage: the file is not usable
            case RuntimeException ignored -> Reason.CORRUPT;
            default -> Reason.IO;
        };
        String detail = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName()
                : e.getMessage();
        String message = switch (reason) {
            case CORRUPT -> "not a readable MP3 file (" + detail + ")";
            case NOT_FOUND -> "file not found";
            case ACCESS_DENIED -> "access denied";
            default -> "read error (" + detail + ")";
        };
        return new MediaAccessException(reason, file, message, e);
    }
}
