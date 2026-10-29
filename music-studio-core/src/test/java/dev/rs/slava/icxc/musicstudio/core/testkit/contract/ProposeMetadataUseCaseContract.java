package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.file;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link ProposeMetadataUseCase} (spec X02). Every implementation (the core service, a server-side
 * implementation, ...) extends this class. The use case is pure: files in, proposals out, nothing written.
 */
public abstract class ProposeMetadataUseCaseContract {

    /** @return a new implementation under test */
    protected abstract ProposeMetadataUseCase create();

    protected static InputPattern pattern(String source) {
        return ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(source)).pattern();
    }

    protected static ExtractionSpec specOf(String pattern) {
        return ExtractionSpec.of(pattern(pattern));
    }

    private ProposalSet run(ExtractionSpec spec, ManualEdits edits, ScannedFile... files) {
        return create().propose(new ProposeRequest(List.of(files), spec, edits));
    }

    @Test
    public void nullRequestIsRejected() {
        assertThatThrownBy(() -> create().propose(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    public void emptyInputGivesEmptyResult() {
        ProposalSet set = run(specOf("{title}"), ManualEdits.NONE);
        assertThat(set.proposals()).isEmpty();
        assertThat(set.matched()).isZero();
        assertThat(set.ambiguous()).isZero();
        assertThat(set.noMatch()).isZero();
    }

    @Test
    public void proposalsFollowTheInputOrderAndKeepTheFile() {
        ScannedFile c = file("c - 3.mp3");
        ScannedFile a = file("a - 1.mp3");
        ScannedFile b = file("b.mp3");
        ProposalSet set = run(specOf("{artist} - {title}"), ManualEdits.NONE, c, a, b);
        assertThat(set.proposals()).extracting(Proposal::file).containsExactly(c, a, b);
        assertThat(set.proposals().get(0).file()).isSameAs(c);
    }

    @Test
    public void statusRules() {
        ProposalSet set = run(specOf("{artist} - {title}"), ManualEdits.NONE, file("A - B.mp3"), file("nothing.mp3"),
                file("A - B - C.mp3"));
        assertThat(set.proposals()).extracting(Proposal::status)
                .containsExactly(MatchStatus.MATCHED, MatchStatus.NO_MATCH, MatchStatus.AMBIGUOUS);
        assertThat(set.proposals()).extracting(Proposal::exportable).containsExactly(true, false, false);
        assertThat(set.matched()).isEqualTo(1);
        assertThat(set.ambiguous()).isEqualTo(1);
        assertThat(set.noMatch()).isEqualTo(1);
    }

    @Test
    public void withoutPatternTheStatusIsNoPattern() {
        ExtractionSpec spec = new ExtractionSpec(Optional.empty(),
                Map.of(Field.GENRE, new FieldOverride.Fixed(new FieldValue.Genres(List.of(new Genre("Pop"))))),
                MergeMode.OVERWRITE, Optional.empty(), false);
        ProposalSet set = run(spec, ManualEdits.NONE, file("anything.mp3"));
        assertThat(set.proposals().get(0).status()).isEqualTo(MatchStatus.NO_PATTERN);
        assertThat(set.proposals().get(0).exportable()).isTrue();
        assertThat(set.matched() + set.ambiguous() + set.noMatch()).isZero();
    }

    @Test
    public void noMatchFileIsLeftExactlyAsItWas() {
        ScannedFile existing = file("nothing.mp3", meta(Field.TITLE, "Old", Field.COMMENT, "keep"));
        ExtractionSpec spec = new ExtractionSpec(Optional.of(pattern("{artist} - {title}")),
                Map.of(Field.GENRE, new FieldOverride.Fixed(new FieldValue.Genres(List.of(new Genre("Pop"))))),
                MergeMode.OVERWRITE, Optional.empty(), false);
        Proposal proposal = run(spec, ManualEdits.NONE, existing).proposals().get(0);
        assertThat(proposal.status()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(proposal.proposed()).isEqualTo(proposal.existing());
        assertThat(proposal.changes()).isEmpty();
    }

    @Test
    public void changesAreExactlyTheDifferencesBetweenExistingAndProposed() {
        ScannedFile f = file("New - Song.mp3", meta(Field.ARTIST, "Old", Field.TITLE, "Song", Field.COMMENT, "c"));
        Proposal proposal = run(specOf("{artist} - {title}"), ManualEdits.NONE, f).proposals().get(0);
        assertThat(proposal.changes()).extracting(c -> c.field()).containsExactly(Field.ARTIST);
        assertThat(proposal.changes().get(0).before()).contains(new FieldValue.Text("Old"));
        assertThat(proposal.changes().get(0).after()).contains(new FieldValue.Text("New"));
        assertThat(proposal.existing().differingFields(proposal.proposed())).containsExactly(Field.ARTIST);
        assertThat(proposal.proposed().get(Field.COMMENT)).contains(new FieldValue.Text("c"));
    }

    @Test
    public void resultsAreImmutable() {
        ProposalSet set = run(specOf("{artist} - {title}"), ManualEdits.NONE, file("A - B.mp3"));
        assertThatThrownBy(() -> set.proposals().clear()).isInstanceOf(UnsupportedOperationException.class);
        Proposal proposal = set.proposals().get(0);
        assertThatThrownBy(() -> proposal.changes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> proposal.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> proposal.extracted().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void requestListIsNotRetainedByReference() {
        List<ScannedFile> files = new ArrayList<>(List.of(file("A - B.mp3")));
        ProposeRequest request = new ProposeRequest(files, specOf("{artist} - {title}"), ManualEdits.NONE);
        files.add(file("C - D.mp3"));
        assertThat(create().propose(request).proposals()).hasSize(1);
    }

    @Test
    public void sameRequestSameResult() {
        ProposeMetadataUseCase useCase = create();
        ProposeRequest request = new ProposeRequest(List.of(file("A - B.mp3"), file("x.mp3"), file("A - B - C.mp3")),
                specOf("{artist} - {title}"), ManualEdits.NONE);
        ProposalSet first = useCase.propose(request);
        for (int i = 0; i < 50; i++) {
            assertThat(useCase.propose(request)).isEqualTo(first);
        }
        assertThat(create().propose(request)).isEqualTo(first);
    }

    @Test
    public void threadSafe() throws Exception {
        ProposeMetadataUseCase useCase = create();
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(file("Artist " + i + " - Song " + i + (i % 5 == 0 ? " - x" : "") + ".mp3"));
        }
        ProposeRequest request = new ProposeRequest(files, specOf("{artist} - {title}"), ManualEdits.NONE);
        ProposalSet expected = useCase.propose(request);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<ProposalSet>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> useCase.propose(request)));
            }
            for (Future<ProposalSet> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
