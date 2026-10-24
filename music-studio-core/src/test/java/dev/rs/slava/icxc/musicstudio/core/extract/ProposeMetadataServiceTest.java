package dev.rs.slava.icxc.musicstudio.core.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering.NumberingOrder;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProposeMetadataServiceTest {

    private static final Path ROOT = Path.of("/m").toAbsolutePath();
    private final ProposeMetadataService service = new ProposeMetadataService();

    private static ScannedFile file(String relative) {
        return ScannedFiles.untagged(ROOT.resolve(relative));
    }

    private static ScannedFile file(String relative, MediaMetadata existing) {
        return ScannedFiles.of(ROOT.resolve(relative), existing);
    }

    private static MediaMetadata meta(Object... fieldValues) {
        MediaMetadata metadata = MediaMetadata.empty();
        for (int i = 0; i < fieldValues.length; i += 2) {
            metadata = metadata.with((Field) fieldValues[i], (FieldValue) fieldValues[i + 1]);
        }
        return metadata;
    }

    private static ExtractionSpec spec(String pattern, Map<Field, FieldOverride> overrides, MergeMode mode,
                                       Optional<AutoNumbering> numbering, boolean accept) {
        return new ExtractionSpec(Optional.ofNullable(pattern).map(ExtractRecordsTest::pattern), overrides, mode,
                numbering, accept);
    }

    private ProposalSet propose(ExtractionSpec spec, ManualEdits edits, ScannedFile... files) {
        return service.propose(new ProposeRequest(List.of(files), spec, edits));
    }

    private ProposalSet propose(ExtractionSpec spec, ScannedFile... files) {
        return propose(spec, ManualEdits.NONE, files);
    }

    @Test
    void onePerFileInInputOrder() {
        ProposalSet set = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{title}")),
                file("c.mp3"), file("a.mp3"), file("b.mp3"));
        assertThat(set.proposals()).extracting(p -> p.file().path().getFileName().toString())
                .containsExactly("c.mp3", "a.mp3", "b.mp3");
    }

    @Test
    void storyExample() {
        ProposalSet set = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{album}/{artist} - {title} - {year}")),
                file("Best Of 2020/Artist - Song - 2020.mp3", meta(Field.TITLE, new Text("Old"))));
        Proposal proposal = set.proposals().getFirst();
        assertThat(proposal.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(proposal.exportable()).isTrue();
        assertThat(proposal.extracted()).containsOnlyKeys(Field.ALBUM, Field.ARTIST, Field.TITLE, Field.YEAR);
        assertThat(proposal.proposed()).isEqualTo(meta(Field.TITLE, new Text("Song"), Field.ARTIST,
                new Text("Artist"), Field.ALBUM, new Text("Best Of 2020"), Field.YEAR, new YearValue(2020)));
        assertThat(proposal.changes()).allMatch(c -> c.origin() == ChangeOrigin.PATTERN).hasSize(4);
        assertThat(proposal.changes().getFirst()).isEqualTo(new FieldChange(Field.TITLE,
                Optional.of(new Text("Old")), Optional.of(new Text("Song")), ChangeOrigin.PATTERN));
        assertThat(set.matched()).isEqualTo(1);
        assertThat(set.exportable()).isEqualTo(1);
    }

    @Test
    void everyPlaceholderIsVisibleEvenIfNotUsed() {
        ExtractionSpec spec = spec("{artist} - {title}", Map.of(Field.ARTIST, new FieldOverride.KeepExisting()),
                MergeMode.FILL_EMPTY, Optional.empty(), false);
        Proposal proposal = propose(spec, file("A - B.mp3", meta(Field.TITLE, new Text("Kept"),
                Field.ARTIST, new Text("Old Artist")))).proposals().getFirst();
        assertThat(proposal.extracted()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B");
        assertThat(proposal.proposed()).isEqualTo(proposal.existing());
        assertThat(proposal.changes()).isEmpty();
    }

    @Test
    void noMatchKeepsExistingAndIgnoresFixedAndManualValues() {
        ScannedFile file = file("Bad Name.mp3", meta(Field.TITLE, new Text("Old")));
        ExtractionSpec spec = spec("{artist} - {title}", Map.of(Field.GENRE, new FieldOverride.Fixed(
                Genres.of("Pop"))), MergeMode.OVERWRITE, Optional.empty(), false);
        ManualEdits edits = new ManualEdits(Map.of(file.path(), Map.of(Field.ALBUM,
                new FieldEdit.Assign(new Text("X")))), Set.of());
        Proposal proposal = propose(spec, edits, file).proposals().getFirst();
        assertThat(proposal.status()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(proposal.proposed()).isEqualTo(proposal.existing());
        assertThat(proposal.changes()).isEmpty();
        assertThat(proposal.exportable()).isFalse();
        assertThat(proposal.matchDetail()).hasValueSatisfying(d -> assertThat(d).contains(" - "));
        assertThat(proposal.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.NO_MATCH);
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.file()).contains(file.path());
        });
    }

    @Test
    void ambiguousIsNotExportableUnlessAccepted() {
        ScannedFile file = file("A - B - C.mp3");
        Proposal ambiguous = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{artist} - {title}")), file)
                .proposals().getFirst();
        assertThat(ambiguous.status()).isEqualTo(MatchStatus.AMBIGUOUS);
        assertThat(ambiguous.proposed().display(Field.ARTIST)).contains("A");
        assertThat(ambiguous.alternative()).containsEntry(Field.ARTIST, "A - B");
        assertThat(ambiguous.exportable()).isFalse();
        assertThat(ambiguous.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.AMBIGUOUS_MATCH);
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.message()).contains("A - B");
        });

        Proposal accepted = propose(spec("{artist} - {title}", Map.of(), MergeMode.OVERWRITE, Optional.empty(), true),
                file).proposals().getFirst();
        assertThat(accepted.status()).isEqualTo(MatchStatus.AMBIGUOUS_ACCEPTED);
        assertThat(accepted.exportable()).isTrue();
        assertThat(accepted.issues()).singleElement().satisfies(i -> assertThat(i.severity())
                .isEqualTo(Severity.WARNING));
    }

    @Test
    void manualAcceptanceForOneFileOnly() {
        ScannedFile one = file("A - B - C.mp3");
        ScannedFile two = file("D - E - F.mp3");
        ProposalSet set = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{artist} - {title}")),
                new ManualEdits(Map.of(), Set.of(one.path())), one, two);
        assertThat(set.proposals()).extracting(Proposal::status)
                .containsExactly(MatchStatus.AMBIGUOUS_ACCEPTED, MatchStatus.AMBIGUOUS);
        assertThat(set.ambiguous()).isEqualTo(2);
    }

    @Test
    void fixedValuesForAllFilesWinOverThePattern() {
        ExtractionSpec spec = spec("{genre} - {title}", Map.of(Field.GENRE, new FieldOverride.Fixed(
                Genres.of("Balkan")), Field.ALBUM_ARTIST, new FieldOverride.Fixed(new Text("Various Artists"))),
                MergeMode.OVERWRITE, Optional.empty(), false);
        ProposalSet set = propose(spec, file("Rock - One.mp3"), file("Pop - Two.mp3"));
        for (Proposal proposal : set.proposals()) {
            assertThat(proposal.proposed().get(Field.GENRE)).contains(Genres.of("Balkan"));
            assertThat(proposal.proposed().display(Field.ALBUM_ARTIST)).contains("Various Artists");
            assertThat(proposal.changes()).filteredOn(c -> c.field() == Field.GENRE).singleElement()
                    .satisfies(c -> assertThat(c.origin()).isEqualTo(ChangeOrigin.FIXED));
        }
    }

    @Test
    void noPatternWithFixedValue() {
        ProposalSet set = propose(spec(null, Map.of(Field.GENRE, new FieldOverride.Fixed(Genres.of("Pop"))),
                MergeMode.OVERWRITE, Optional.empty(), false), file("x.mp3"));
        assertThat(set.proposals().getFirst().status()).isEqualTo(MatchStatus.NO_PATTERN);
        assertThat(set.proposals().getFirst().exportable()).isTrue();
        assertThat(set.proposals().getFirst().proposed().get(Field.GENRE)).contains(Genres.of("Pop"));
    }

    @Test
    void keepExisting() {
        ExtractionSpec spec = spec("{artist} - {title}", Map.of(Field.ARTIST, new FieldOverride.KeepExisting()),
                MergeMode.OVERWRITE, Optional.empty(), false);
        Proposal kept = propose(spec, file("A - B.mp3", meta(Field.ARTIST, new Text("Real")))).proposals()
                .getFirst();
        assertThat(kept.proposed().display(Field.ARTIST)).contains("Real");
        assertThat(kept.proposed().display(Field.TITLE)).contains("B");
        Proposal absent = propose(spec, file("A - B.mp3")).proposals().getFirst();
        assertThat(absent.proposed().get(Field.ARTIST)).isEmpty();
    }

    @Test
    void mergeModes() {
        ScannedFile file = file("Album/Song.mp3", meta(Field.TITLE, new Text("Existing")));
        Proposal overwrite = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{album}/{title}")), file)
                .proposals().getFirst();
        assertThat(overwrite.proposed().display(Field.TITLE)).contains("Song");
        ExtractionSpec fill = spec("{album}/{title}", Map.of(Field.GENRE, new FieldOverride.Fixed(Genres.of("Pop"))),
                MergeMode.FILL_EMPTY, Optional.empty(), false);
        Proposal filled = propose(fill, file).proposals().getFirst();
        assertThat(filled.proposed().display(Field.TITLE)).contains("Existing");
        assertThat(filled.proposed().display(Field.ALBUM)).contains("Album");
        assertThat(filled.proposed().display(Field.GENRE)).contains("Pop");
        ScannedFile withGenre = file("Album/Song.mp3", meta(Field.GENRE, Genres.of("Rock")));
        assertThat(propose(fill, withGenre).proposals().getFirst().proposed().display(Field.GENRE)).contains("Rock");
    }

    @Test
    void equalValueIsNoChange() {
        Proposal proposal = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{title}")),
                file("Song.mp3", meta(Field.TITLE, new Text("Song")))).proposals().getFirst();
        assertThat(proposal.changes()).isEmpty();
    }

    @Test
    void autoNumberingByFileNamePerFolder() {
        ExtractionSpec spec = spec("{title}", Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, true)), false);
        ProposalSet set = propose(spec, file("A/c.mp3"), file("A/b.mp3"), file("A/a.mp3"), file("B/y.mp3"),
                file("B/x.mp3"));
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TRACK).orElse(""))
                .containsExactly("3/3", "2/3", "1/3", "2/2", "1/2");
        assertThat(set.proposals().getFirst().changes()).filteredOn(c -> c.field() == Field.TRACK).singleElement()
                .satisfies(c -> assertThat(c.origin()).isEqualTo(ChangeOrigin.AUTO_NUMBER));
    }

    @Test
    void autoNumberingSkipsNotExportableFiles() {
        ExtractionSpec spec = spec("{artist} - {title}", Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, false)), false);
        ProposalSet set = propose(spec, file("a - x.mp3"), file("b - c - d.mp3"), file("bad.mp3"), file("c - y.mp3"));
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TRACK).orElse("-"))
                .containsExactly("1", "-", "-", "2");
    }

    @Test
    void autoNumberingByExistingTrack() {
        ExtractionSpec spec = spec(null, Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.EXISTING_TRACK, false)), false);
        ProposalSet set = propose(spec, file("a.mp3", meta(Field.TRACK, Position.of(5))),
                file("b.mp3", meta(Field.TRACK, Position.of(2))), file("c.mp3"));
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TRACK).orElse(""))
                .containsExactly("2", "1", "3");
    }

    @Test
    void autoNumberingFillEmptyKeepsExistingTracks() {
        ExtractionSpec spec = spec(null, Map.of(), MergeMode.FILL_EMPTY,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, false)), false);
        ProposalSet set = propose(spec, file("a.mp3", meta(Field.TRACK, Position.of(7))), file("b.mp3"));
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TRACK).orElse(""))
                .containsExactly("7", "2");
    }

    @Test
    void manualEditsWinAndAreMarkedManual() {
        ScannedFile a = file("a.mp3", meta(Field.ALBUM, new Text("Old")));
        ExtractionSpec spec = spec("{title}", Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, false)), false);
        ManualEdits edits = new ManualEdits(Map.of(a.path(), Map.of(Field.TITLE, new FieldEdit.Assign(new Text("Mine")),
                Field.ALBUM, new FieldEdit.Clear(), Field.TRACK, new FieldEdit.Assign(Position.of(9))),
                ROOT.resolve("unknown.mp3"), Map.of(Field.TITLE, new FieldEdit.Clear())), Set.of());
        Proposal proposal = propose(spec, edits, a).proposals().getFirst();
        assertThat(proposal.proposed().display(Field.TITLE)).contains("Mine");
        assertThat(proposal.proposed().get(Field.ALBUM)).isEmpty();
        assertThat(proposal.proposed().display(Field.TRACK)).contains("9");
        assertThat(proposal.changes()).allMatch(c -> c.origin() == ChangeOrigin.MANUAL);
    }

    @Test
    void positionExceedsTotalOnlyForChangedValues() {
        Proposal extracted = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{track} {title}")),
                file("13of12 Song.mp3")).proposals().getFirst();
        assertThat(extracted.issues()).extracting(i -> i.code()).containsExactly(IssueCode.POSITION_EXCEEDS_TOTAL);
        Proposal unchanged = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{title}")),
                file("Song.mp3", meta(Field.TRACK, Position.of(13, 12)))).proposals().getFirst();
        assertThat(unchanged.issues()).isEmpty();
    }

    @Test
    void emptyBatch() {
        ProposalSet set = propose(ExtractionSpec.of(ExtractRecordsTest.pattern("{title}")));
        assertThat(set.proposals()).isEmpty();
        assertThat(set.exportable()).isZero();
    }

    @Test
    void pureFastAndRepeatable() {
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            files.add(file("2020 - Album %d/%02d - Artist %d - Title number %d.mp3".formatted(i / 20, i % 20 + 1,
                    i, i), meta(Field.TITLE, new Text("Old " + i))));
        }
        ExtractionSpec spec = spec("{year} - {album}/{*} - {artist} - {title}", Map.of(Field.GENRE,
                new FieldOverride.Fixed(Genres.of("Pop")), Field.ALBUM_ARTIST,
                new FieldOverride.Fixed(new Text("Various"))), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, true)), false);
        ProposeRequest request = new ProposeRequest(files, spec, ManualEdits.NONE);
        service.propose(request); // warm-up
        long start = System.nanoTime();
        ProposalSet first = service.propose(request);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        assertThat(first.matched()).isEqualTo(5_000);
        assertThat(service.propose(request)).isEqualTo(first);
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> service.propose(null)).isInstanceOf(NullPointerException.class);
    }
}
