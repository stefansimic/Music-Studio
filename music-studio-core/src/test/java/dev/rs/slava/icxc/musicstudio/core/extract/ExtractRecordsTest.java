package dev.rs.slava.icxc.musicstudio.core.extract;

import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.genres;
import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.pattern;
import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.file;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** X01 (preconditions of the extract records and the use case) and X05 (record invariants). */
class ExtractRecordsTest {

    private static final ScannedFile FILE = file("A - B.mp3");

    private static void npe(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String argument) {
        assertThatThrownBy(call).isInstanceOf(NullPointerException.class).hasMessageContaining(argument);
    }

    private static void iae(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- use case -----------------------------------------------------------------------------------------------

    @Test
    void proposeNullRequest() {
        npe(() -> new ProposeMetadataService().propose(null), "request");
    }

    @Test
    void proposeRequestPreconditions() {
        ExtractionSpec spec = ExtractionSpec.of(pattern("{title}"));
        npe(() -> new ProposeRequest(null, spec, ManualEdits.NONE), "files");
        npe(() -> new ProposeRequest(java.util.Arrays.asList(FILE, null), spec, ManualEdits.NONE), "files");
        npe(() -> new ProposeRequest(List.of(), null, ManualEdits.NONE), "spec");
        npe(() -> new ProposeRequest(List.of(), spec, null), "edits");
    }

    @Test
    void proposeRequestCopiesTheList() {
        List<ScannedFile> files = new ArrayList<>(List.of(FILE));
        ProposeRequest request = new ProposeRequest(files, ExtractionSpec.of(pattern("{title}")), ManualEdits.NONE);
        files.clear();
        assertThat(request.files()).hasSize(1);
        assertThatThrownBy(() -> request.files().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- ExtractionSpec -----------------------------------------------------------------------------------------

    @Test
    void specPreconditions() {
        npe(() -> new ExtractionSpec(null, Map.of(), MergeMode.OVERWRITE, Optional.empty(), false), "pattern");
        npe(() -> new ExtractionSpec(Optional.empty(), null, MergeMode.OVERWRITE, Optional.empty(), false), "overrides");
        npe(() -> new ExtractionSpec(Optional.empty(), Map.of(), null, Optional.empty(), false), "mergeMode");
        npe(() -> new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE, null, false), "autoNumbering");
        npe(() -> ExtractionSpec.of(null), "pattern");
        Map<Field, FieldOverride> withNull = new HashMap<>();
        withNull.put(Field.TITLE, null);
        npe(() -> new ExtractionSpec(Optional.empty(), withNull, MergeMode.OVERWRITE, Optional.empty(), false),
                "overrides");
    }

    @Test
    void fixedValueOfTheWrongTypeIsRejected() {
        iae(() -> new ExtractionSpec(Optional.empty(),
                Map.of(Field.YEAR, new FieldOverride.Fixed(text("abc"))), MergeMode.OVERWRITE, Optional.empty(), false));
        iae(() -> new ExtractionSpec(Optional.empty(),
                Map.of(Field.GENRE, new FieldOverride.Fixed(text("Pop"))), MergeMode.OVERWRITE, Optional.empty(), false));
        iae(() -> new ExtractionSpec(Optional.empty(),
                Map.of(Field.TRACK, new FieldOverride.Fixed(new FieldValue.YearValue(2000))), MergeMode.OVERWRITE,
                Optional.empty(), false));
        assertThatThrownBy(() -> new ExtractionSpec(Optional.empty(),
                Map.of(Field.YEAR, new FieldOverride.Fixed(text("abc"))), MergeMode.OVERWRITE, Optional.empty(), false))
                .hasMessageContaining("type");
    }

    @Test
    void specIsEmptyOnlyWithoutPatternOverridesAndNumbering() {
        assertThat(new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE, Optional.empty(), true).isEmpty())
                .isTrue();
        assertThat(ExtractionSpec.of(pattern("{title}")).isEmpty()).isFalse();
        assertThat(new ExtractionSpec(Optional.empty(), Map.of(Field.GENRE, new FieldOverride.KeepExisting()),
                MergeMode.OVERWRITE, Optional.empty(), false).isEmpty()).isFalse();
        assertThat(new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false)), false).isEmpty()).isFalse();
    }

    @Test
    void specDefaultsAndImmutability() {
        ExtractionSpec spec = ExtractionSpec.of(pattern("{title}"));
        assertThat(spec.mergeMode()).isEqualTo(MergeMode.OVERWRITE);
        assertThat(spec.acceptAmbiguous()).isFalse();
        assertThat(spec.autoNumbering()).isEmpty();
        assertThat(spec.overrides()).isEmpty();
        assertThatThrownBy(() -> spec.overrides().put(Field.TITLE, new FieldOverride.KeepExisting()))
                .isInstanceOf(UnsupportedOperationException.class);
        Map<Field, FieldOverride> mutable = new HashMap<>();
        ExtractionSpec copy = new ExtractionSpec(Optional.empty(), mutable, MergeMode.OVERWRITE, Optional.empty(), false);
        mutable.put(Field.TITLE, new FieldOverride.KeepExisting());
        assertThat(copy.overrides()).isEmpty();
    }

    @Test
    void overridesAndEditsRejectNull() {
        npe(() -> new FieldOverride.Fixed(null), "value");
        npe(() -> new FieldEdit.Set(null), "value");
        npe(() -> new AutoNumbering(null, false), "order");
    }

    // ---- ManualEdits --------------------------------------------------------------------------------------------

    @Test
    void manualEditsPreconditions() {
        npe(() -> new ManualEdits(null, Set.of()), "edits");
        npe(() -> new ManualEdits(Map.of(), null), "acceptedAmbiguous");
        Map<Path, Map<Field, FieldEdit>> badValue = new HashMap<>();
        badValue.put(FILE.path(), null);
        npe(() -> new ManualEdits(badValue, Set.of()), "edits");
        Map<Field, FieldEdit> inner = new HashMap<>();
        inner.put(Field.TITLE, null);
        npe(() -> new ManualEdits(Map.of(FILE.path(), inner), Set.of()), "edits");
    }

    @Test
    void manualSetOfTheWrongTypeIsRejected() {
        iae(() -> new ManualEdits(Map.of(FILE.path(), Map.of(Field.YEAR, new FieldEdit.Set(text("x")))), Set.of()));
        iae(() -> new ManualEdits(Map.of(FILE.path(), Map.of(Field.GENRE, new FieldEdit.Set(text("x")))), Set.of()));
    }

    @Test
    void manualEditsRejectRelativePaths() {
        iae(() -> new ManualEdits(Map.of(Path.of("relative/a.mp3"), Map.of(Field.TITLE, new FieldEdit.Set(text("x")))),
                Set.of()));
        iae(() -> new ManualEdits(Map.of(), Set.of(Path.of("relative/a.mp3"))));
    }

    @Test
    void scannedFilesMustBeAbsolute() {
        iae(() -> new ScannedFile(Path.of("relative/a.mp3"), FILE.format(), FILE.fingerprint(), FILE.tags(),
                FILE.conflicts()));
    }

    @Test
    void noneIsEmptyAndEditsAreCopied() {
        assertThat(ManualEdits.NONE.edits()).isEmpty();
        assertThat(ManualEdits.NONE.acceptedAmbiguous()).isEmpty();
        Map<Field, FieldEdit> inner = new HashMap<>(Map.of(Field.TITLE, new FieldEdit.Clear()));
        ManualEdits edits = new ManualEdits(Map.of(FILE.path(), inner), Set.of(FILE.path()));
        assertThatThrownBy(() -> edits.edits().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> edits.acceptedAmbiguous().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- ProposedChange / Proposal / ProposalSet ----------------------------------------------------------------

    @Test
    void proposedChangeMustChangeSomething() {
        iae(() -> new ProposedChange(Field.TITLE, Optional.of(text("a")), Optional.of(text("a")), ChangeOrigin.PATTERN));
        iae(() -> new ProposedChange(Field.TITLE, Optional.empty(), Optional.empty(), ChangeOrigin.PATTERN));
        npe(() -> new ProposedChange(null, Optional.empty(), Optional.of(text("a")), ChangeOrigin.PATTERN), "field");
        npe(() -> new ProposedChange(Field.TITLE, null, Optional.of(text("a")), ChangeOrigin.PATTERN), "before");
        npe(() -> new ProposedChange(Field.TITLE, Optional.empty(), null, ChangeOrigin.PATTERN), "after");
        npe(() -> new ProposedChange(Field.TITLE, Optional.empty(), Optional.of(text("a")), null), "origin");
        assertThat(new ProposedChange(Field.TITLE, Optional.of(text("a")), Optional.empty(), ChangeOrigin.MANUAL).after())
                .isEmpty();
    }

    @Test
    void proposalPreconditions() {
        MediaMetadata empty = MediaMetadata.empty();
        npe(() -> new Proposal(null, MatchStatus.MATCHED, Optional.empty(), Map.of(), empty, List.of(), List.of()), "file");
        npe(() -> new Proposal(FILE, null, Optional.empty(), Map.of(), empty, List.of(), List.of()), "status");
        npe(() -> new Proposal(FILE, MatchStatus.MATCHED, null, Map.of(), empty, List.of(), List.of()), "matchDetail");
        npe(() -> new Proposal(FILE, MatchStatus.MATCHED, Optional.empty(), null, empty, List.of(), List.of()), "extracted");
        npe(() -> new Proposal(FILE, MatchStatus.MATCHED, Optional.empty(), Map.of(), null, List.of(), List.of()), "proposed");
        npe(() -> new Proposal(FILE, MatchStatus.MATCHED, Optional.empty(), Map.of(), empty, null, List.of()), "changes");
        npe(() -> new Proposal(FILE, MatchStatus.MATCHED, Optional.empty(), Map.of(), empty, List.of(), null), "issues");
    }

    @Test
    void proposalExportableOnlyForCleanStatuses() {
        for (MatchStatus status : MatchStatus.values()) {
            Proposal proposal = new Proposal(FILE, status, Optional.empty(), Map.of(), MediaMetadata.empty(), List.of(),
                    List.of());
            assertThat(proposal.exportable()).as(status.name()).isEqualTo(status == MatchStatus.NO_PATTERN
                    || status == MatchStatus.MATCHED || status == MatchStatus.AMBIGUOUS_ACCEPTED);
        }
    }

    @Test
    void proposalSetInvariants() {
        Proposal p = new Proposal(FILE, MatchStatus.MATCHED, Optional.empty(), Map.of(), MediaMetadata.empty(), List.of(),
                List.of());
        new ProposalSet(List.of(p), 1, 0, 0);
        new ProposalSet(List.of(p), 0, 0, 0);
        iae(() -> new ProposalSet(List.of(p), 1, 1, 0));
        iae(() -> new ProposalSet(List.of(p), -1, 0, 0));
        iae(() -> new ProposalSet(List.of(p), 0, -1, 0));
        iae(() -> new ProposalSet(List.of(p), 0, 0, -1));
        iae(() -> new ProposalSet(List.of(), 0, 0, 1));
        npe(() -> new ProposalSet(null, 0, 0, 0), "proposals");
        assertThatThrownBy(() -> new ProposalSet(List.of(p), 1, 0, 0).proposals().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void positionOfZeroIsAValueButNotLargerThan9999() {
        assertThat(new FieldValue.Position(0, OptionalInt.empty()).display()).isEqualTo("0");
        iae(() -> new FieldValue.Position(10_000, OptionalInt.empty()));
        iae(() -> new FieldValue.Position(-1, OptionalInt.empty()));
        assertThat(genres("A").values()).hasSize(1);
    }
}
