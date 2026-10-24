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
 * What a tag writer can write for one format.
 *
 * @param format            the format; not null
 * @param writableVersions  non-empty; every version belongs to a family the format can hold; copied
 * @param writableEncodings contains AUTO; copied
 * @param writableFields    non-empty; copied
 * @param frontCover        whether a front cover can be written (story 009)
 */
public record FormatCapabilities(MediaFormat format, Set<TagVersion> writableVersions,
                                 Set<V2Encoding> writableEncodings, Set<Field> writableFields, boolean frontCover) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component or element is null
     * @throws IllegalArgumentException if an invariant is violated
     */
    public FormatCapabilities {
        Require.nonNull(format, "format");
        Require.noNullElements(writableVersions, "writableVersions");
        Require.noNullElements(writableEncodings, "writableEncodings");
        Require.noNullElements(writableFields, "writableFields");
        Require.that(!writableVersions.isEmpty(), "writableVersions must not be empty");
        Require.that(writableVersions.stream().allMatch(v -> format.tagFamilies().contains(v.family())),
                "every writable version must fit %s", format);
        Require.that(writableEncodings.contains(V2Encoding.AUTO), "writableEncodings must contain AUTO");
        Require.that(!writableFields.isEmpty(), "writableFields must not be empty");
        writableVersions = Set.copyOf(writableVersions);
        writableEncodings = Set.copyOf(writableEncodings);
        writableFields = Set.copyOf(writableFields);
    }

    /**
     * The selected versions and the encoding this writer cannot write.
     *
     * @param options the options; not null
     * @return one ERROR UNSUPPORTED_WRITE_VERSION per unsupported version or encoding; empty = supported; never
     *         null
     * @throws NullPointerException if {@code options} is null
     */
    public List<Issue> unsupported(TagWriteOptions options) {
        Require.nonNull(options, "options");
        List<Issue> issues = new ArrayList<>();
        for (TagVersion version : options.selectedVersions()) {
            if (!writableVersions.contains(version)) {
                issues.add(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION,
                        version.label() + " cannot be written by this tag writer"));
            }
        }
        if (!writableEncodings.contains(options.v2Encoding())) {
            issues.add(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION,
                    "the text encoding " + options.v2Encoding() + " cannot be written by this tag writer"));
        }
        return List.copyOf(issues);
    }
}
