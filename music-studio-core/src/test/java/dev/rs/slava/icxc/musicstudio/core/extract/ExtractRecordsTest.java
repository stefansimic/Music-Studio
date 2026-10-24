package dev.rs.slava.icxc.musicstudio.core.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtractRecordsTest {

    private static final Path ROOT = Path.of("/m").toAbsolutePath();

    static InputPattern pattern(String source) {
        return ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(source)).pattern();
    }

    @Test
    void problemsOfTheSpec() {
        Optional<AutoNumbering> numbering = Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME,
                false));
        assertThat(ExtractionSpec.problems(Optional.of(pattern("{track} - {title}")), Map.of(), numbering))
                .singleElement().asString().contains("pattern and auto-numbering");
        assertThat(ExtractionSpec.problems(Optional.empty(),
                Map.of(Field.TRACK, new FieldOverride.Fixed(Position.of(1))), numbering))
                .singleElement().asString().contains("auto-numbered");
        assertThat(ExtractionSpec.problems(Optional.of(pattern("{title}")), Map.of(), numbering)).isEmpty();
        assertThatThrownBy(() -> new ExtractionSpec(Optional.of(pattern("{track} {title}")), Map.of(),
                MergeMode.OVERWRITE, numbering, false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("auto-numbering");
    }

    @Test
    void fixedValueMustFitItsField() {
        assertThatThrownBy(() -> new ExtractionSpec(Optional.empty(),
                Map.of(Field.YEAR, new FieldOverride.Fixed(new Text("2020"))), MergeMode.OVERWRITE,
                Optional.empty(), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void specCopiesItsMap() {
        Map<Field, FieldOverride> overrides = new HashMap<>();
        overrides.put(Field.GENRE, new FieldOverride.KeepExisting());
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(), overrides, MergeMode.OVERWRITE, Optional.empty(),
                false);
        overrides.put(Field.TITLE, new FieldOverride.KeepExisting());
        assertThat(spec.overrides()).containsOnlyKeys(Field.GENRE);
        assertThat(ExtractionSpec.of(pattern("{title}")).mergeMode()).isEqualTo(MergeMode.OVERWRITE);
    }

    @Test
    void requestRejectsDuplicatePaths() {
        ScannedFile file = ScannedFiles.untagged(ROOT.resolve("a.mp3"));
        assertThatThrownBy(() -> new ProposeRequest(List.of(file, file), ExtractionSpec.of(pattern("{title}")),
                ManualEdits.NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualEditsValidation() {
        assertThat(ManualEdits.NONE.edits()).isEmpty();
        assertThatThrownBy(() -> new ManualEdits(Map.of(Path.of("rel.mp3"), Map.of()), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualEdits(Map.of(), Set.of(Path.of("rel.mp3"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualEdits(Map.of(ROOT.resolve("a.mp3"),
                Map.of(Field.YEAR, new FieldEdit.Assign(new Text("x")))), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ManualEdits.NONE.of(ROOT.resolve("x.mp3"))).isEmpty();
    }

    @Test
    void fieldChangeMustChange() {
        assertThatThrownBy(() -> new FieldChange(Field.TITLE, Optional.of(new Text("a")), Optional.of(new Text("a")),
                ChangeOrigin.PATTERN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposalInvariants() {
        ScannedFile file = ScannedFiles.of(ROOT.resolve("a.mp3"), MediaMetadata.empty().with(Field.TITLE,
                new Text("Old")));
        MediaMetadata changed = file.tags().effective().with(Field.TITLE, new Text("New"));
        // changes missing
        assertThatThrownBy(() -> new Proposal(file, MatchStatus.MATCHED, Optional.empty(), Map.of(), Map.of(),
                changed, List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        // NO_MATCH must keep existing
        assertThatThrownBy(() -> new Proposal(file, MatchStatus.NO_MATCH, Optional.of("x"), Map.of(), Map.of(),
                changed, List.of(new FieldChange(Field.TITLE, Optional.of(new Text("Old")),
                Optional.of(new Text("New")), ChangeOrigin.PATTERN)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // alternative only for ambiguous
        assertThatThrownBy(() -> new Proposal(file, MatchStatus.MATCHED, Optional.empty(), Map.of(),
                Map.of(Field.TITLE, "x"), file.tags().effective(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // NO_PATTERN has nothing extracted
        assertThatThrownBy(() -> new Proposal(file, MatchStatus.NO_PATTERN, Optional.empty(),
                Map.of(Field.TITLE, "x"), Map.of(), file.tags().effective(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposalSetCounters() {
        ScannedFile file = ScannedFiles.untagged(ROOT.resolve("a.mp3"));
        Proposal proposal = new Proposal(file, MatchStatus.NO_MATCH, Optional.of("x"), Map.of(), Map.of(),
                file.tags().effective(), List.of(), List.of());
        assertThatThrownBy(() -> new ProposalSet(List.of(proposal), 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        ProposalSet set = ProposalSet.of(List.of(proposal));
        assertThat(set.noMatch()).isEqualTo(1);
        assertThat(set.exportable()).isZero();
        assertThat(MatchStatus.AMBIGUOUS.exportable()).isFalse();
        assertThat(MatchStatus.AMBIGUOUS_ACCEPTED.exportable()).isTrue();
    }
}
