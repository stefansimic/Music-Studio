package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.List;
import java.util.Set;

/**
 * Tells a frontend what can be written and whether chosen write options are possible. Pure queries, no I/O.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use.
 */
public interface WriteOptionsUseCase {

    /**
     * @param format a format in {@link #writableFormats()}; not null
     * @return what the installed writer can write for that format; never null
     * @throws NullPointerException     if format is null
     * @throws IllegalArgumentException if the format is not writable
     */
    FormatCapabilities capabilities(MediaFormat format);

    /** @return the formats that can be written; never null, not empty */
    Set<MediaFormat> writableFormats();

    /**
     * @param options the chosen options; not null
     * @param formats the formats they will be applied to; not null, non-empty, all writable
     * @return ERROR UNSUPPORTED_WRITE_VERSION issues, one per unsupported selected version or encoding and format;
     *         empty if the options are possible; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if formats is empty or contains a non-writable format
     */
    List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats);
}
