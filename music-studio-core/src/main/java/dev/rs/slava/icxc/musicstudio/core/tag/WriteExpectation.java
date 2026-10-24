package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The slot rules of a tag write, the same for every writer (ADR 0011): which tags a file holds after a write and
 * which values a TagReader will report for them.
 *
 * <ul>
 *   <li>A selected ID3v2 version: that version with {@code changes.applyTo(current ID3v2 values)}; an existing tag
 *       of another v2 version is converted.</li>
 *   <li>A selected ID3v1 version: exactly {@code request.id3v1()} as read back. An ID3v1.1 block with track 0 is
 *       indistinguishable from ID3v1.0 (the track byte decides), so it is expected as ID3v1.0.</li>
 *   <li>A family set to NONE: the existing tag is kept unchanged (OTHER_VERSION_KEPT warning) or removed
 *       (OTHER_VERSION_REMOVED info) depending on {@code removeOtherVersions}.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class WriteExpectation {

    private static final Set<Field> V1_FIELDS = Set.of(Field.TITLE, Field.ARTIST, Field.ALBUM, Field.YEAR,
            Field.COMMENT, Field.TRACK, Field.GENRE);

    private WriteExpectation() {
    }

    /**
     * The layers a conforming writer leaves in the target, as a TagReader reports them.
     *
     * @param current the source's layers; not null
     * @param request the write request; not null
     * @return the expected layers in TagFamily order; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<TagLayer> layersAfter(List<TagLayer> current, TagWriteRequest request) {
        Require.noNullElements(current, "current");
        Require.nonNull(request, "request");
        TagWriteOptions options = request.options();
        List<TagLayer> layers = new ArrayList<>();
        Optional<TagLayer> v2 = find(current, TagFamily.ID3V2);
        if (options.id3v2().version().isPresent()) {
            MediaMetadata values = request.changes().applyTo(v2.map(TagLayer::metadata).orElse(MediaMetadata.empty()));
            layers.add(new TagLayer(options.id3v2().version().get(), values));
        } else if (!options.removeOtherVersions()) {
            v2.ifPresent(layers::add);
        }
        Optional<TagLayer> v1 = find(current, TagFamily.ID3V1);
        if (request.id3v1().isPresent()) {
            Id3v1Values values = request.id3v1().get();
            TagVersion version = values.version() == Id3v1Choice.V1_1 && values.track() == 0
                    ? TagVersion.ID3V1_0
                    : values.version().version().orElseThrow();
            layers.add(new TagLayer(version, values.readBack()));
        } else if (!options.removeOtherVersions()) {
            v1.ifPresent(layers::add);
        }
        return List.copyOf(layers);
    }

    /**
     * Warnings about tags of families set to NONE.
     *
     * @param current  the source's layers; not null
     * @param options  the write options; not null
     * @param proposed the proposed final metadata, to name stale values of a kept tag; not null
     * @return OTHER_VERSION_KEPT (WARNING) or OTHER_VERSION_REMOVED (INFO) per affected family; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<Issue> slotIssues(List<TagLayer> current, TagWriteOptions options, MediaMetadata proposed) {
        Require.noNullElements(current, "current");
        Require.nonNull(options, "options");
        Require.nonNull(proposed, "proposed");
        List<Issue> issues = new ArrayList<>();
        if (options.id3v2().version().isEmpty()) {
            find(current, TagFamily.ID3V2).ifPresent(layer -> issues.add(issue(layer, options, proposed, null)));
        }
        if (options.id3v1().version().isEmpty()) {
            find(current, TagFamily.ID3V1).ifPresent(layer -> issues.add(issue(layer, options, proposed, V1_FIELDS)));
        }
        return List.copyOf(issues);
    }

    private static Issue issue(TagLayer layer, TagWriteOptions options, MediaMetadata proposed, Set<Field> holdable) {
        String label = layer.version().label();
        if (options.removeOtherVersions()) {
            return Issue.info(IssueCode.OTHER_VERSION_REMOVED, "the existing " + label + " tag is removed");
        }
        String stale = layer.metadata().differingFields(proposed).stream()
                .filter(field -> holdable == null || holdable.contains(field))
                .map(Field::placeholder)
                .collect(Collectors.joining(", "));
        return Issue.warning(IssueCode.OTHER_VERSION_KEPT, "the existing " + label + " tag is kept unchanged"
                + (stale.isEmpty() ? "" : "; it still shows old values for " + stale)
                + " (use --remove-other-versions to remove it)");
    }

    private static Optional<TagLayer> find(List<TagLayer> layers, TagFamily family) {
        return layers.stream().filter(layer -> layer.version().family() == family).findFirst();
    }
}
