package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * User-selected tag write options (MP3/ID3). {@code removeOtherVersions} is the settings checkbox "Remove other tag
 * versions" (CLI {@code --remove-other-versions}): unchecked, a tag family set to NONE keeps its existing tag
 * (OTHER_VERSION_KEPT warning); checked, it is removed (OTHER_VERSION_REMOVED). A selected version always converts
 * the existing tag of its family.
 *
 * <p>Invariants ({@link #problems} must be empty): UTF8 only with V2_4; an encoding other than AUTO needs an ID3v2
 * version; NONE + NONE only with removeOtherVersions (= "strip all tags").
 *
 * @param id3v2               not null
 * @param id3v1               not null
 * @param v2Encoding          not null
 * @param v1TextMode          not null
 * @param removeOtherVersions remove an existing tag of a family set to NONE
 */
public record TagWriteOptions(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding v2Encoding, V1TextMode v1TextMode,
                              boolean removeOtherVersions) {

    /** ID3v2.3 + ID3v1.1, AUTO encoding, ASCII v1, other versions kept. */
    public static final TagWriteOptions DEFAULT =
            new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII, false);

    /**
     * Validates the combination.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException with the {@link #problems} messages if the combination is invalid
     */
    public TagWriteOptions {
        List<String> problems = problems(id3v2, id3v1, v2Encoding, v1TextMode, removeOtherVersions);
        Require.that(problems.isEmpty(), "%s", String.join("; ", problems));
    }

    /**
     * Pure validation, used by frontends to grey out invalid combinations.
     *
     * @param id3v2               not null
     * @param id3v1               not null
     * @param encoding            not null
     * @param mode                not null
     * @param removeOtherVersions the checkbox
     * @return user-facing English messages; empty if the combination is valid; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<String> problems(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding encoding, V1TextMode mode,
                                        boolean removeOtherVersions) {
        Require.nonNull(id3v2, "id3v2");
        Require.nonNull(id3v1, "id3v1");
        Require.nonNull(encoding, "encoding");
        Require.nonNull(mode, "mode");
        List<String> problems = new ArrayList<>();
        if (encoding == V2Encoding.UTF8 && id3v2 != Id3v2Choice.V2_4 && id3v2 != Id3v2Choice.NONE) {
            problems.add("UTF-8 text encoding needs ID3v2.4");
        }
        if (encoding != V2Encoding.AUTO && id3v2 == Id3v2Choice.NONE) {
            problems.add("a text encoding needs an ID3v2 version");
        }
        if (id3v2 == Id3v2Choice.NONE && id3v1 == Id3v1Choice.NONE && !removeOtherVersions) {
            problems.add("nothing to write: choose an ID3v2 or ID3v1 version, or remove other versions to strip all "
                    + "tags");
        }
        return List.copyOf(problems);
    }

    /** @return the versions that will be written; unmodifiable, never null */
    public Set<TagVersion> selectedVersions() {
        EnumSet<TagVersion> versions = EnumSet.noneOf(TagVersion.class);
        id3v2.version().ifPresent(versions::add);
        id3v1.version().ifPresent(versions::add);
        return Collections.unmodifiableSet(versions);
    }
}
