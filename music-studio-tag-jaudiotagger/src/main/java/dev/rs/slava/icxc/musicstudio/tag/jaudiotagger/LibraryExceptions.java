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

/** Maps every exception of the library to a {@link MediaAccessException}: no library type may cross the port. */
final class LibraryExceptions {

    private LibraryExceptions() {}

    /**
     * @param file the file that was being read
     * @param e    any exception thrown by the library (including unchecked ones)
     * @return the matching port exception, with {@code e} as cause
     */
    static MediaAccessException map(Path file, Exception e) {
        if (e instanceof CannotReadException || e instanceof InvalidAudioFrameException || e instanceof TagException) {
            return new MediaAccessException(Reason.CORRUPT, file, "not a readable MP3 file (" + describe(e) + ")", e);
        }
        if (e instanceof NoSuchFileException || e instanceof FileNotFoundException && !isPermissionProblem(e)) {
            return new MediaAccessException(Reason.NOT_FOUND, file, "no such file", e);
        }
        if (e instanceof AccessDeniedException || isPermissionProblem(e)) {
            return new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied", e);
        }
        if (e instanceof ReadOnlyFileException || e instanceof IOException) {
            return new MediaAccessException(Reason.IO, file, "cannot read the file: " + describe(e), e);
        }
        // a bug or an unexpected file layout inside the library: the file is not usable, the scan must go on
        return new MediaAccessException(Reason.CORRUPT, file, "unexpected error while reading the file: " + describe(e), e);
    }

    private static boolean isPermissionProblem(Exception e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("permission denied");
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
