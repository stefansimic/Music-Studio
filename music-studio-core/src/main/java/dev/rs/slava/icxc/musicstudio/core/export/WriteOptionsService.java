package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Default {@link WriteOptionsUseCase}: asks the {@link TagWriter} what it can do. Stateless. */
public final class WriteOptionsService implements WriteOptionsUseCase {

    private final TagWriter writer;

    /** @param writer the active tag writer; not null */
    public WriteOptionsService(TagWriter writer) {
        this.writer = Require.nonNull(writer, "writer");
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(writer.writableFormats().contains(format), "%s cannot be written", format);
        return writer.capabilities(format);
    }

    @Override
    public Set<MediaFormat> writableFormats() {
        return writer.writableFormats();
    }

    @Override
    public List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats) {
        Require.nonNull(options, "options");
        Require.noNullElements(formats, "formats");
        Require.that(!formats.isEmpty(), "formats must not be empty");
        List<Issue> issues = new ArrayList<>();
        for (MediaFormat format : EnumSet.copyOf(formats)) {
            if (!writer.writableFormats().contains(format)) {
                issues.add(Issue.error(IssueCode.UNSUPPORTED_FORMAT, format + " files cannot be written"));
                continue;
            }
            for (Issue issue : writer.capabilities(format).unsupported(options)) {
                if (!issues.contains(issue)) {
                    issues.add(issue);
                }
            }
        }
        return List.copyOf(issues);
    }
}
