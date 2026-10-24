package dev.rs.slava.icxc.musicstudio.core.extract;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering.NumberingOrder;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValueParser;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.ParsedValue;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Story 002 acceptance criteria and edge cases on batches of real-world names (tester additions). */
class ProposeMetadataEdgeCaseTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();
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

    private static FieldValue parsed(Field field, String raw) {
        return ((ParsedValue.Valid) FieldValueParser.parse(field, raw)).value();
    }

    private ProposalSet propose(String pattern, Map<Field, FieldOverride> overrides, MergeMode mode,
                                Optional<AutoNumbering> numbering, boolean accept, ScannedFile... files) {
        ExtractionSpec spec = new ExtractionSpec(Optional.ofNullable(pattern).map(ExtractRecordsTest::pattern),
                overrides, mode, numbering, accept);
        return service.propose(new ProposeRequest(List.of(files), spec, ManualEdits.NONE));
    }

    private ProposalSet propose(String pattern, ScannedFile... files) {
        return propose(pattern, Map.of(), MergeMode.OVERWRITE, Optional.empty(), false, files);
    }

    private static Proposal byName(ProposalSet set, String fileName) {
        return set.proposals().stream().filter(p -> p.file().path().getFileName().toString().equals(fileName))
                .findFirst().orElseThrow();
    }

    @Test
    void separatorMissingInAFewFilesOfABatch() {
        ProposalSet set = propose("{album}/{artist} - {title} - {year}",
                file("Best Of 2020/Artist - Song - 2020.mp3"),
                file("Best Of 2020/Jay-Z - Track - 2019.mp3"),
                file("Best Of 2020/Artist-Song Two-2021.mp3"),
                file("Best Of 2020/Artist  -  Song Three  -  2018.mp3"),
                file("Best Of 2020/Artist Song Four 2017.mp3"));
        assertThat(set.proposals()).extracting(Proposal::status).containsExactly(MatchStatus.MATCHED,
                MatchStatus.MATCHED, MatchStatus.MATCHED, MatchStatus.MATCHED, MatchStatus.NO_MATCH);
        assertThat(set.matched()).isEqualTo(4);
        assertThat(set.noMatch()).isEqualTo(1);
        assertThat(set.exportable()).isEqualTo(4);
        assertThat(byName(set, "Artist-Song Two-2021.mp3").proposed()).isEqualTo(meta(Field.TITLE,
                new Text("Song Two"), Field.ARTIST, new Text("Artist"), Field.ALBUM, new Text("Best Of 2020"),
                Field.YEAR, new YearValue(2021)));
        assertThat(byName(set, "Artist  -  Song Three  -  2018.mp3").proposed().display(Field.TITLE))
                .contains("Song Three");
        Proposal noMatch = byName(set, "Artist Song Four 2017.mp3");
        assertThat(noMatch.matchDetail()).hasValueSatisfying(d -> assertThat(d).contains("Artist Song Four 2017"));
        assertThat(noMatch.issues()).extracting(i -> i.code()).containsExactly(IssueCode.NO_MATCH);
    }

    @Test
    void everyPlaceholderHasAnExtractedValuePerFile() {
        ProposalSet set = propose("{year} - {album}/{track}. {artist} - {title}",
                file("2020 - Best Of/01. A - One.mp3"), file("2020 - Best Of/02.B - Two.mp3"),
                file("1999 - Другой/3of12. Ђорђе - Песма.mp3"));
        for (Proposal proposal : set.proposals()) {
            assertThat(proposal.status()).as("%s", proposal.file().path()).isEqualTo(MatchStatus.MATCHED);
            assertThat(proposal.extracted()).containsOnlyKeys(Field.YEAR, Field.ALBUM, Field.TRACK, Field.ARTIST,
                    Field.TITLE);
            assertThat(proposal.extracted().values()).allSatisfy(v -> assertThat(v).isNotBlank());
        }
        Proposal cyrillic = byName(set, "3of12. Ђорђе - Песма.mp3");
        assertThat(cyrillic.proposed().get(Field.TRACK)).contains(Position.of(3, 12));
        assertThat(cyrillic.proposed().get(Field.ALBUM)).contains(new Text("Другой"));
    }

    @Test
    void fieldsNotTouchedByTheRunKeepTheirExistingValues() {
        MediaMetadata existing = meta(Field.TITLE, new Text("Old"), Field.GENRE, Genres.of("Rock", "Pop"),
                Field.COMMENT, new Text("ripped 2004\nsecond line"), Field.DISC, Position.of(1, 2),
                Field.ALBUM_ARTIST, new Text("Various"));
        Proposal proposal = propose("{artist} - {title}", file("A - New.mp3", existing)).proposals().getFirst();
        for (Field field : List.of(Field.GENRE, Field.COMMENT, Field.DISC, Field.ALBUM_ARTIST)) {
            assertThat(proposal.proposed().get(field)).as("%s", field).isEqualTo(existing.get(field));
        }
        assertThat(proposal.changes()).extracting(FieldChange::field).containsExactly(Field.TITLE, Field.ARTIST);
    }

    @Test
    void nfdFileNameEqualToExistingNfcTagIsNoChange() {
        String nfd = Normalizer.normalize("Đorđe Balašević - Računajte na nas", Normalizer.Form.NFD);
        MediaMetadata existing = meta(Field.ARTIST, new Text("Đorđe Balašević"),
                Field.TITLE, new Text("Računajte na nas"));
        Proposal proposal = propose("{artist} - {title}", file(nfd + ".mp3", existing)).proposals().getFirst();
        assertThat(proposal.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(proposal.changes()).as("NFD vs NFC must not count as a change").isEmpty();
        assertThat(proposal.proposed()).isEqualTo(existing);
    }

    @Test
    void fixedValueTypedInNfdEqualsNfc() {
        FieldValue nfd = parsed(Field.ALBUM_ARTIST, Normalizer.normalize("Razni izvođači", Normalizer.Form.NFD));
        Proposal proposal = propose(null, Map.of(Field.ALBUM_ARTIST, new FieldOverride.Fixed(nfd)),
                MergeMode.OVERWRITE, Optional.empty(), false,
                file("x.mp3", meta(Field.ALBUM_ARTIST, new Text("Razni izvođači")))).proposals().getFirst();
        assertThat(proposal.changes()).isEmpty();
    }

    @Test
    void fixedValuesApplyToEveryExportableFileButNotToSkippedOnes() {
        Map<Field, FieldOverride> fixed = Map.of(Field.GENRE, new FieldOverride.Fixed(parsed(Field.GENRE, "Balkan")),
                Field.ALBUM_ARTIST, new FieldOverride.Fixed(parsed(Field.ALBUM_ARTIST, "Various Artists")));
        ProposalSet set = propose("{artist} - {title}", fixed, MergeMode.OVERWRITE, Optional.empty(), false,
                file("A - One.mp3"), file("B - Two - Live.mp3"), file("Nope.mp3"), file("Ђ - Три.mp3"));
        assertThat(byName(set, "A - One.mp3").proposed().get(Field.GENRE)).contains(Genres.of("Balkan"));
        assertThat(byName(set, "Ђ - Три.mp3").proposed().display(Field.ALBUM_ARTIST)).contains("Various Artists");
        // NO_MATCH and unaccepted AMBIGUOUS are never written: they carry no change at all … (NO_MATCH: D8)
        assertThat(byName(set, "Nope.mp3").changes()).isEmpty();
        assertThat(byName(set, "Nope.mp3").exportable()).isFalse();
        assertThat(byName(set, "B - Two - Live.mp3").exportable()).isFalse();
    }

    @Test
    void keepExistingAndFillEmptyNeverClear() {
        MediaMetadata existing = meta(Field.ARTIST, new Text("Real Artist"), Field.YEAR, new YearValue(1986));
        ProposalSet set = propose("{artist} - {title} - {year}", Map.of(Field.ARTIST,
                new FieldOverride.KeepExisting()), MergeMode.FILL_EMPTY, Optional.empty(), false,
                file("Wrong - Song - 2020.mp3", existing));
        Proposal proposal = set.proposals().getFirst();
        assertThat(proposal.proposed().get(Field.ARTIST)).contains(new Text("Real Artist"));
        assertThat(proposal.proposed().get(Field.YEAR)).contains(new YearValue(1986));
        assertThat(proposal.proposed().get(Field.TITLE)).contains(new Text("Song"));
        assertThat(proposal.changes()).allSatisfy(c -> assertThat(c.after()).isPresent());
    }

    @Test
    void overwriteIsTheDefaultOfExtractionSpecOf() {
        assertThat(ExtractionSpec.of(ExtractRecordsTest.pattern("{title}")).mergeMode()).isEqualTo(MergeMode.OVERWRITE);
    }

    @Test
    void autoNumberingOfARealAlbumWithoutNumbersInTheNames() {
        ProposalSet set = propose("{album}/{artist} - {title}", Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(NumberingOrder.FILE_NAME, true)), false,
                file("Album B/Zed - Last.mp3"), file("Album A/Beta - Two.mp3"), file("Album A/alpha - One.mp3"),
                file("Album A/Gamma - Three.mp3"), file("Album A/Broken.mp3"));
        assertThat(byName(set, "alpha - One.mp3").proposed().get(Field.TRACK)).contains(Position.of(1, 3));
        assertThat(byName(set, "Beta - Two.mp3").proposed().get(Field.TRACK)).contains(Position.of(2, 3));
        assertThat(byName(set, "Gamma - Three.mp3").proposed().get(Field.TRACK)).contains(Position.of(3, 3));
        assertThat(byName(set, "Zed - Last.mp3").proposed().get(Field.TRACK)).contains(Position.of(1, 1));
        assertThat(byName(set, "Broken.mp3").proposed().get(Field.TRACK)).isEmpty();
    }

    @Test
    void acceptedAmbiguousUsesTheChosenSplit() {
        ProposalSet set = propose("{artist} - {title}", Map.of(), MergeMode.OVERWRITE, Optional.empty(), true,
                file("Artist - Song - Remix.mp3"));
        Proposal proposal = set.proposals().getFirst();
        assertThat(proposal.status()).isEqualTo(MatchStatus.AMBIGUOUS_ACCEPTED);
        assertThat(proposal.proposed().display(Field.ARTIST)).contains("Artist");
        assertThat(proposal.proposed().display(Field.TITLE)).contains("Song - Remix");
        assertThat(proposal.alternative()).containsEntry(Field.ARTIST, "Artist - Song")
                .containsEntry(Field.TITLE, "Remix");
    }

    @Test
    void manualFixOfAnAmbiguousRowAppliesOnlyWhenAccepted() {
        ScannedFile file = file("Artist - Song - Remix.mp3");
        ManualEdits edits = new ManualEdits(Map.of(file.path(), Map.of(Field.ARTIST,
                new FieldEdit.Assign(new Text("Artist - Song")), Field.TITLE, new FieldEdit.Assign(new Text("Remix")))),
                Set.of(file.path()));
        ProposalSet set = service.propose(new ProposeRequest(List.of(file),
                ExtractionSpec.of(ExtractRecordsTest.pattern("{artist} - {title}")), edits));
        Proposal proposal = set.proposals().getFirst();
        assertThat(proposal.status()).isEqualTo(MatchStatus.AMBIGUOUS_ACCEPTED);
        assertThat(proposal.exportable()).isTrue();
        assertThat(proposal.proposed().display(Field.ARTIST)).contains("Artist - Song");
        assertThat(proposal.proposed().display(Field.TITLE)).contains("Remix");
    }

    @Test
    void emojiAndCjkValuesSurviveIntoTheProposal() {
        ProposalSet set = propose("{artist} - {title}", file("🔥 坂本龍一 - Song 🎶.mp3"));
        Proposal proposal = set.proposals().getFirst();
        assertThat(proposal.proposed().get(Field.ARTIST)).contains(new Text("🔥 坂本龍一"));
        assertThat(proposal.proposed().get(Field.TITLE)).contains(new Text("Song 🎶"));
    }
}
