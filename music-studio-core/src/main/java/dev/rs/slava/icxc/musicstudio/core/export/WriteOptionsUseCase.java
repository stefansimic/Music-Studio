package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.List;
import java.util.Set;

/**
 * Answers what can be written, so frontends can grey out options before planning.
 *
 * <p><b>Thread-safety:</b> pure and safe for concurrent use.
 */
public interface WriteOptionsUseCase {

    /**
     * @param format a writable format; not null (NPE), in {@link #writableFormats()} (IAE)
     * @return the capabilities of the active writer for {@code format}; never null
     */
    FormatCapabilities capabilities(MediaFormat format);

    /** @return the formats the active writer supports; not empty, unmodifiable */
    Set<MediaFormat> writableFormats();

    /**
     * Validates options against the writer for every format in {@code formats}.
     *
     * <p><b>Preconditions:</b> both not null; {@code formats} not empty (IAE).
     * <p><b>Postconditions:</b> the list is empty iff a plan with these options will not fail with
     * {@code UNSUPPORTED_WRITE_VERSION} or {@code UNSUPPORTED_FORMAT}.
     *
     * @param options the options; not null
     * @param formats the formats the files have; not null, not empty
     * @return errors, no duplicates; never null
     */
    List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats);
}
