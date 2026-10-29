package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a {@code TagWriter} can produce for one format.
 *
 * @param format            not null
 * @param writableVersions  tag versions the writer can write; not empty
 * @param writableEncodings v2 text encodings the writer can write ({@code AUTO} is always possible); not empty
 * @param writableFields    fields the writer can store; not empty
 * @param frontCover        true if a front cover can be written (story 009)
 */
public record FormatCapabilities(MediaFormat format, Set<TagVersion> writableVersions,
                                 Set<V2Encoding> writableEncodings, Set<Field> writableFields, boolean frontCover) {

    public FormatCapabilities {
        Require.nonNull(format, "format");
        Require.noNullElements(writableVersions, "writableVersions");
        Require.noNullElements(writableEncodings, "writableEncodings");
        Require.noNullElements(writableFields, "writableFields");
        Require.that(!writableVersions.isEmpty(), "writableVersions must not be empty");
        Require.that(!writableEncodings.isEmpty(), "writableEncodings must not be empty");
        Require.that(!writableFields.isEmpty(), "writableFields must not be empty");
        writableVersions = Set.copyOf(writableVersions);
        writableEncodings = Set.copyOf(writableEncodings);
        writableFields = Set.copyOf(writableFields);
    }

    /**
     * Pure check of options against what this writer can do.
     *
     * @param options the options to check; not null
     * @return one {@code UNSUPPORTED_WRITE_VERSION} error per version or encoding this writer cannot honour; empty
     *         if the options are fine; never null
     * @throws NullPointerException if {@code options} is null
     */
    public List<Issue> unsupported(TagWriteOptions options) {
        Require.nonNull(options, "options");
        List<Issue> issues = new ArrayList<>();
        for (TagVersion version : options.selectedVersions()) {
            if (!writableVersions.contains(version)) {
                issues.add(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION,
                        version.label() + " cannot be written for " + format + " by this writer"));
            }
        }
        if (options.v2Encoding() != V2Encoding.AUTO && !writableEncodings.contains(options.v2Encoding())) {
            issues.add(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION,
                    "Text encoding " + options.v2Encoding() + " cannot be written for " + format + " by this writer"));
        }
        return List.copyOf(issues);
    }
}
