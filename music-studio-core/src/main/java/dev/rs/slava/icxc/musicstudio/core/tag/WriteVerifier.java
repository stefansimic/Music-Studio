package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares a written file, as read back, with what the plan promised. Genres are compared by their display text,
 * because ID3v2.2/2.3 store several genres in one string.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class WriteVerifier {

    /** Allowed difference between the source's and the copy's duration. */
    public static final Duration DURATION_TOLERANCE = Duration.ofMillis(50);

    private WriteVerifier() {
    }

    /**
     * Verifies a written file.
     *
     * @param expected     the expected layers ({@link WriteExpectation#layersAfter}); not null
     * @param sourceStream the source's stream info; not null
     * @param actual       what a reader found in the written file; not null
     * @return one ERROR VERIFY_MISMATCH per difference; empty = the write is as planned; never null
     * @throws NullPointerException if an argument is null
     */
    public static List<Issue> verify(List<TagLayer> expected, Optional<StreamInfo> sourceStream,
                                     TagReadResult actual) {
        Require.noNullElements(expected, "expected");
        Require.nonNull(sourceStream, "sourceStream");
        Require.nonNull(actual, "actual");
        List<String> problems = new ArrayList<>();
        for (TagFamily family : TagFamily.values()) {
            Optional<TagLayer> want = find(expected, family);
            Optional<TagLayer> got = actual.layer(family);
            if (want.isEmpty() && got.isEmpty()) {
                continue;
            }
            if (want.isEmpty()) {
                problems.add("unexpected " + got.get().version().label() + " tag");
                continue;
            }
            if (got.isEmpty()) {
                problems.add(want.get().version().label() + " tag missing");
                continue;
            }
            if (want.get().version() != got.get().version()) {
                problems.add("expected " + want.get().version().label() + ", found " + got.get().version().label());
            }
            compareValues(want.get().version().label(), want.get().metadata(), got.get().metadata(), problems);
        }
        sourceStream.ifPresent(source -> {
            if (actual.stream().isEmpty()) {
                problems.add("the audio stream cannot be read in the copy");
            } else if (source.duration().minus(actual.stream().get().duration()).abs()
                    .compareTo(DURATION_TOLERANCE) > 0) {
                problems.add("duration: expected " + source.duration().toMillis() + " ms, found "
                        + actual.stream().get().duration().toMillis() + " ms");
            }
        });
        actual.issues().stream().filter(issue -> issue.severity() == Severity.ERROR)
                .forEach(issue -> problems.add("the copy reads with an error: " + issue.message()));
        return problems.stream().map(p -> Issue.error(IssueCode.VERIFY_MISMATCH, p)).toList();
    }

    private static void compareValues(String label, MediaMetadata want, MediaMetadata got, List<String> problems) {
        for (Field field : Field.values()) {
            Optional<FieldValue> w = want.get(field);
            Optional<FieldValue> g = got.get(field);
            boolean equal = field == Field.GENRE
                    ? w.map(FieldValue::display).equals(g.map(FieldValue::display))
                    : w.equals(g);
            if (!equal) {
                problems.add(label + " " + field.placeholder() + ": expected " + show(w) + ", found " + show(g));
            }
        }
    }

    private static String show(Optional<FieldValue> value) {
        return value.map(v -> "\"" + v.display() + "\"").orElse("(none)");
    }

    private static Optional<TagLayer> find(List<TagLayer> layers, TagFamily family) {
        return layers.stream().filter(layer -> layer.version().family() == family).findFirst();
    }
}
