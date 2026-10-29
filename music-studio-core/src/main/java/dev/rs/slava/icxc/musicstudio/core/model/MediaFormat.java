package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** A supported media container format. */
public enum MediaFormat {
    MP3(Set.of("mp3"), Set.of(TagFamily.ID3V2, TagFamily.ID3V1));

    private final Set<String> extensions;
    private final Set<TagFamily> tagFamilies;

    MediaFormat(Set<String> extensions, Set<TagFamily> tagFamilies) {
        this.extensions = extensions;
        this.tagFamilies = tagFamilies;
    }

    /** @return lower-case file extensions without dot; unmodifiable, never empty */
    public Set<String> extensions() {
        return extensions;
    }

    /** @return the tag families a file of this format can hold; unmodifiable, never empty */
    public Set<TagFamily> tagFamilies() {
        return tagFamilies;
    }

    /**
     * Case-insensitive extension match on the file name. A leading dot does not hide the extension
     * ({@code .mp3} is an mp3 file name); only the last extension counts ({@code a.mp3.txt} is not an mp3).
     *
     * @param fileName name of a file, without directories; not null
     * @return the matching format, empty if none
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
}
