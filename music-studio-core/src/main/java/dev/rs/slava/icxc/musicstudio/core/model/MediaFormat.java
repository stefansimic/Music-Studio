package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** A media container format the application can handle. Detected by file extension. */
public enum MediaFormat {
    /** MPEG-1/2 Layer III audio with ID3 tags. */
    MP3(Set.of("mp3"), Set.of(TagFamily.ID3V2, TagFamily.ID3V1));

    private final Set<String> extensions;
    private final Set<TagFamily> tagFamilies;

    MediaFormat(Set<String> extensions, Set<TagFamily> tagFamilies) {
        this.extensions = extensions;
        this.tagFamilies = tagFamilies;
    }

    /**
     * @return the tag families a file of this format can hold; non-empty, unmodifiable
     */
    public Set<TagFamily> tagFamilies() {
        return tagFamilies;
    }

    /**
     * @return the file extensions of this format; lower case, without dot, non-empty, unmodifiable
     */
    public Set<String> extensions() {
        return extensions;
    }

    /**
     * Detects the format of a file by its extension, case-insensitively. A name that only consists of the
     * extension (".mp3", a hidden file) still counts.
     *
     * @param fileName the file name (not a path); not null
     * @return the format, or empty if the extension is unknown or missing
     * @throws NullPointerException if {@code fileName} is null
     */
    public static Optional<MediaFormat> ofFileName(String fileName) {
        Require.nonNull(fileName, "fileName");
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (MediaFormat format : values()) {
            if (format.extensions.contains(extension)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the lower-case extension of a file name, without dot.
     *
     * @param fileName the file name; not null
     * @return the extension, or empty if the name contains no dot or ends with one
     * @throws NullPointerException if {@code fileName} is null
     */
    public static Optional<String> extensionOf(String fileName) {
        Require.nonNull(fileName, "fileName");
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
