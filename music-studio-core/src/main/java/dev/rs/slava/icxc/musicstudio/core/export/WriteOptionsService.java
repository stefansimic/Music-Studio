package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** {@link WriteOptionsUseCase} answered from the {@link TagWriter}'s capabilities. */
public final class WriteOptionsService implements WriteOptionsUseCase {

    private final TagWriter writer;

    /** @param writer the tag writer; not null */
    public WriteOptionsService(TagWriter writer) {
        this.writer = Require.nonNull(writer, "writer");
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(writer.writableFormats().contains(format), "not writable: %s", format);
        return writer.capabilities(format);
    }

    @Override
    public Set<MediaFormat> writableFormats() {
        return Set.copyOf(writer.writableFormats());
    }

    @Override
    public List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats) {
        Require.nonNull(options, "options");
        Require.noNullElements(formats, "formats");
        Require.that(!formats.isEmpty(), "formats must not be empty");
        List<Issue> issues = new ArrayList<>();
        for (MediaFormat format : EnumSet.copyOf(formats)) {
            issues.addAll(capabilities(format).unsupported(options));
        }
        return List.copyOf(issues);
    }
}
