package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * User-selected tag write options (MP3/ID3; ignored for formats without versions).
 *
 * <p>{@code removeOtherVersions} is the settings checkbox "Remove other tag versions" (default off; CLI
 * {@code --remove-other-versions}). A selected version always converts the existing tag of its family. A family set
 * to NONE keeps its existing tag (warning {@code OTHER_VERSION_KEPT}) unless this is on (then it is removed,
 * {@code OTHER_VERSION_REMOVED}).
 *
 * <p>Invariants (see {@link #problems}):
 * <ul>
 *   <li>{@code UTF8} only with {@code V2_4}; an encoding other than {@code AUTO} requires an ID3v2 version.</li>
 *   <li>ID3v2 NONE and ID3v1 NONE only with {@code removeOtherVersions} (= explicit "strip all tags").</li>
 * </ul>
 *
 * @param id3v2               ID3v2 version to write; not null
 * @param id3v1               ID3v1 version to write; not null
 * @param v2Encoding          text encoding of ID3v2 frames; not null
 * @param v1TextMode          how Latin text is stored in ID3v1; not null
 * @param v1NonLatinPolicy    what happens to ID3v1 when a v1 field has non-Latin letters; not null
 * @param removeOtherVersions remove tags of families set to NONE
 */
public record TagWriteOptions(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding v2Encoding, V1TextMode v1TextMode,
                              V1NonLatinPolicy v1NonLatinPolicy, boolean removeOtherVersions) {

    /** ID3v2.3, ID3v1.1, automatic encoding, ASCII v1 text, v1 skipped for non-Latin text, other versions kept. */
    public static final TagWriteOptions DEFAULT = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1,
            V2Encoding.AUTO, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false);

    public TagWriteOptions {
        Require.nonNull(id3v2, "id3v2");
        Require.nonNull(id3v1, "id3v1");
        Require.nonNull(v2Encoding, "v2Encoding");
        Require.nonNull(v1TextMode, "v1TextMode");
        Require.nonNull(v1NonLatinPolicy, "v1NonLatinPolicy");
        List<String> problems = problems(id3v2, id3v1, v2Encoding, v1TextMode, removeOtherVersions);
        Require.that(problems.isEmpty(), "%s", String.join("; ", problems));
    }

    /**
     * Pure validation, used by frontends to grey out combinations; the constructor throws with these messages.
     *
     * @param id3v2               not null
     * @param id3v1               not null
     * @param enc                 not null
     * @param mode                not null
     * @param removeOtherVersions the checkbox
     * @return the problems in plain English, each naming the options involved; empty if the combination is valid
     * @throws NullPointerException if an argument is null
     */
    public static List<String> problems(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding enc, V1TextMode mode,
                                        boolean removeOtherVersions) {
        Require.nonNull(id3v2, "id3v2");
        Require.nonNull(id3v1, "id3v1");
        Require.nonNull(enc, "enc");
        Require.nonNull(mode, "mode");
        List<String> problems = new ArrayList<>();
        if (id3v2 == Id3v2Choice.NONE && enc != V2Encoding.AUTO) {
            problems.add("v2Encoding " + enc + " needs an ID3v2 tag, but id3v2 is NONE");
        } else if (enc == V2Encoding.UTF8 && id3v2 != Id3v2Choice.V2_4) {
            problems.add("v2Encoding UTF8 is only valid with id3v2 V2_4, but id3v2 is " + id3v2);
        }
        if (id3v2 == Id3v2Choice.NONE && id3v1 == Id3v1Choice.NONE && !removeOtherVersions) {
            problems.add("id3v2 NONE and id3v1 NONE would write no tag; select removeOtherVersions to strip all tags");
        }
        return List.copyOf(problems);
    }

    /** @return the tag versions these options write (0 to 2 versions); unmodifiable, never null */
    public Set<TagVersion> selectedVersions() {
        Set<TagVersion> versions = EnumSet.noneOf(TagVersion.class);
        id3v2.version().ifPresent(versions::add);
        id3v1.version().ifPresent(versions::add);
        return Set.copyOf(versions);
    }
}
