package dev.rs.slava.icxc.musicstudio.core.extract;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValueParser;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.ParsedValue;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cases from the 002 review. */
class ProposeMetadataServiceReviewTest {

    private static final Path ROOT = Path.of("/m").toAbsolutePath();

    @Test
    void fillEmptyAutoNumberingWarnsAboutDuplicateTrackNumbers() {
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.FILL_EMPTY,
                Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false)), false);
        ProposalSet set = new ProposeMetadataService().propose(new ProposeRequest(List.of(
                ScannedFiles.untagged(ROOT.resolve("a.mp3")),
                ScannedFiles.of(ROOT.resolve("b.mp3"), MediaMetadata.empty().with(Field.TRACK, Position.of(1)))),
                spec, ManualEdits.NONE));
        assertThat(set.proposals()).allSatisfy(p -> assertThat(p.issues()).extracting(i -> i.code())
                .containsExactly(IssueCode.DUPLICATE_TRACK_NUMBER));
    }

    @Test
    void bidiOverridesAreRejectedInText() {
        assertThat(FieldValueParser.parse(Field.TITLE, "A‮evil")).isInstanceOf(ParsedValue.Invalid.class);
        assertThat(FieldValueParser.parse(Field.GENRE, "Pop⁦")).isInstanceOf(ParsedValue.Invalid.class);
        assertThat(FieldValueParser.parse(Field.TITLE, "👨‍👩")).isInstanceOf(ParsedValue.Valid.class);
    }

    @Test
    void autoNumberingByFileNameUsesNaturalOrder() {
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false)), false);
        ProposalSet set = new ProposeMetadataService().propose(new ProposeRequest(List.of(
                ScannedFiles.untagged(ROOT.resolve("10 - x.mp3")), ScannedFiles.untagged(ROOT.resolve("2 - y.mp3")),
                ScannedFiles.untagged(ROOT.resolve("1 - z.mp3"))), spec, ManualEdits.NONE));
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TRACK).orElse(""))
                .containsExactly("3", "2", "1");
    }
}
