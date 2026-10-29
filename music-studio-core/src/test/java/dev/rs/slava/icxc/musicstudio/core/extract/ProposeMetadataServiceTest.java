package dev.rs.slava.icxc.musicstudio.core.extract;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.conflicting;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.file;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The proposal pipeline: existing -> pattern -> fixed -> auto-number -> manual (A01-A25, E20-E22, D03, D04, D07, X05). */
class ProposeMetadataServiceTest {

    private final ProposeMetadataService service = new ProposeMetadataService();

    // ---- helpers ------------------------------------------------------------------------------------------------

    static InputPattern pattern(String source) {
        return ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(source)).pattern();
    }

    static FieldValue.Text text(String value) {
        return new FieldValue.Text(value);
    }

    static FieldValue.Genres genres(String... names) {
        return new FieldValue.Genres(java.util.Arrays.stream(names).map(Genre::new).toList());
    }

    static FieldValue.Position pos(int number) {
        return new FieldValue.Position(number, OptionalInt.empty());
    }

    static FieldValue.Position pos(int number, int total) {
        return new FieldValue.Position(number, OptionalInt.of(total));
    }

    static ExtractionSpec spec(String pattern) {
        return pattern == null ? new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE, Optional.empty(), false)
                : ExtractionSpec.of(pattern(pattern));
    }

    static ExtractionSpec spec(String pattern, MergeMode mode, Map<Field, FieldOverride> overrides,
                               AutoNumbering numbering, boolean accept) {
        return new ExtractionSpec(Optional.ofNullable(pattern).map(ProposeMetadataServiceTest::pattern), overrides, mode,
                Optional.ofNullable(numbering), accept);
    }

    static Map<Field, FieldOverride> fixed(Object... fieldsAndValues) {
        Map<Field, FieldOverride> map = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            Object v = fieldsAndValues[i + 1];
            map.put((Field) fieldsAndValues[i],
                    v instanceof FieldOverride o ? o : new FieldOverride.Fixed((FieldValue) v));
        }
        return map;
    }

    private ProposalSet run(ExtractionSpec spec, ManualEdits edits, ScannedFile... files) {
        return service.propose(new ProposeRequest(List.of(files), spec, edits));
    }

    private ProposalSet run(ExtractionSpec spec, ScannedFile... files) {
        return run(spec, ManualEdits.NONE, files);
    }

    private static Optional<ProposedChange> change(Proposal p, Field field) {
        return p.changes().stream().filter(c -> c.field() == field).findFirst();
    }

    private static ManualEdits edits(ScannedFile file, Object... fieldsAndEdits) {
        Map<Field, FieldEdit> map = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndEdits.length; i += 2) {
            Object e = fieldsAndEdits[i + 1];
            map.put((Field) fieldsAndEdits[i], e instanceof FieldEdit fe ? fe : new FieldEdit.Set((FieldValue) e));
        }
        return new ManualEdits(Map.of(file.path(), map), Set.of());
    }

    // ---- A01-A03 ------------------------------------------------------------------------------------------------

    @Test
    void a01ExtractedValuesPerPlaceholder() {
        ScannedFile f = file("Best Of 2020/Artist - Song - 2020.mp3");
        Proposal p = run(spec("{album}/{artist} - {title} - {year}"), f).proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.extracted()).containsEntry(Field.ALBUM, "Best Of 2020").containsEntry(Field.ARTIST, "Artist")
                .containsEntry(Field.TITLE, "Song").containsEntry(Field.YEAR, "2020").hasSize(4);
        assertThat(p.proposed().values()).containsEntry(Field.YEAR, new FieldValue.YearValue(2020))
                .containsEntry(Field.ALBUM, text("Best Of 2020"));
        assertThat(p.matchDetail()).isEmpty();
    }

    @Test
    void a02AllNinePlaceholdersAreTyped() {
        ScannedFile f = file("AA/Alb (1999)/03-1-Pop; Rock-Art-Remark-Song.mp3");
        Proposal p = run(spec("{albumartist}/{album} ({year})/{track}-{disc}-{genre}-{artist}-{comment}-{title}"), f)
                .proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.extracted()).containsOnlyKeys(Field.values());
        assertThat(p.proposed().values()).containsEntry(Field.TRACK, pos(3)).containsEntry(Field.DISC, pos(1))
                .containsEntry(Field.YEAR, new FieldValue.YearValue(1999))
                .containsEntry(Field.GENRE, genres("Pop", "Rock")).containsEntry(Field.ALBUM_ARTIST, text("AA"))
                .containsEntry(Field.TITLE, text("Song")).containsEntry(Field.COMMENT, text("Remark"));
    }

    @Test
    void a03WildcardIsNotExtracted() {
        Proposal p = run(spec("{*} - {artist} - {title}"), file("Junk - A - B.mp3")).proposals().get(0);
        assertThat(p.extracted()).containsOnlyKeys(Field.ARTIST, Field.TITLE);
        assertThat(p.proposed().values()).containsOnlyKeys(Field.ARTIST, Field.TITLE);
    }

    // ---- A06 / A07 / A07b ---------------------------------------------------------------------------------------

    @Test
    void a06NoMatchIsMarkedWithReasonAndWarning() {
        Proposal p = run(spec("{artist} - {title}"), file("Artist Song.mp3")).proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(p.matchDetail()).contains("separator \" - \" not found after \"Artist Song\"");
        assertThat(p.exportable()).isFalse();
        assertThat(p.extracted()).isEmpty();
        assertThat(p.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.PATTERN_NO_MATCH);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.file()).contains(p.file().path());
        });
    }

    @Test
    void a07NoMatchFileGetsNothing() {
        ScannedFile bad = file("Artist Song.mp3", meta(Field.TITLE, "Old", Field.GENRE, genres("Rock")));
        ScannedFile good = file("A - B.mp3");
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE, fixed(Field.GENRE, genres("Pop")),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, true), false);
        ProposalSet set = run(spec, edits(bad, Field.COMMENT, text("manual")), bad, good);
        Proposal p = set.proposals().get(0);
        assertThat(p.proposed()).isEqualTo(p.existing());
        assertThat(p.changes()).isEmpty();
        assertThat(p.proposed().get(Field.TRACK)).isEmpty();
        // the good file is numbered 1 of 1: the no-match file is not counted
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(1, 1));
    }

    @Test
    void a07bNameBeatsAutoNumber() {
        ExtractionSpec spec = spec("{track} - {title}", MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        ProposalSet set = run(spec, file("07 - A.mp3"), file("02 - B.mp3"));
        assertThat(set.proposals().get(0).proposed().get(Field.TRACK)).contains(pos(7));
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(2));
        assertThat(change(set.proposals().get(0), Field.TRACK).orElseThrow().origin()).isEqualTo(ChangeOrigin.PATTERN);
    }

    @Test
    void a07bFileThatAlreadyHasATrackKeepsItWhenFillingEmpty() {
        ScannedFile keeps = file("b.mp3", meta(Field.TRACK, pos(7)));
        ExtractionSpec spec = spec(null, MergeMode.FILL_EMPTY, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        ProposalSet set = run(spec, file("a.mp3"), keeps, file("c.mp3"));
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(7));
        assertThat(set.proposals().get(1).changes()).isEmpty();
        assertThat(change(set.proposals().get(0), Field.TRACK).orElseThrow().origin()).isEqualTo(ChangeOrigin.AUTO_NUMBER);
        assertThat(change(set.proposals().get(2), Field.TRACK).orElseThrow().origin()).isEqualTo(ChangeOrigin.AUTO_NUMBER);
    }

    // ---- A08-A11 ------------------------------------------------------------------------------------------------

    @Test
    void a08AmbiguousShowsTheChosenSplit() {
        ProposalSet set = run(spec("{artist} - {title}"), file("Artist - Part - Song.mp3"));
        Proposal p = set.proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.AMBIGUOUS);
        assertThat(p.extracted()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.TITLE, "Part - Song");
        assertThat(p.matchDetail().orElseThrow()).contains("Artist").contains("Part - Song");
        assertThat(p.exportable()).isFalse();
        assertThat(p.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.AMBIGUOUS_MATCH);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        });
        assertThat(set.ambiguous()).isEqualTo(1);
        assertThat(set.matched()).isZero();
    }

    @Test
    void a09AcceptViaSpec() {
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE, Map.of(), null, true);
        ProposalSet set = run(spec, file("Artist - Part - Song.mp3"), file("X - Y.mp3"));
        Proposal p = set.proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.AMBIGUOUS_ACCEPTED);
        assertThat(p.exportable()).isTrue();
        assertThat(p.proposed().get(Field.ARTIST)).contains(text("Artist"));
        assertThat(p.proposed().get(Field.TITLE)).contains(text("Part - Song"));
        assertThat(p.issues()).noneMatch(i -> i.severity() == Severity.WARNING || i.severity() == Severity.ERROR);
        assertThat(set.matched()).isEqualTo(2);
        assertThat(set.ambiguous()).isZero();
    }

    @Test
    void a10AcceptPerFile() {
        ScannedFile one = file("A - B - C.mp3");
        ScannedFile two = file("D - E - F.mp3");
        ManualEdits accept = new ManualEdits(Map.of(), Set.of(one.path()));
        ProposalSet set = run(spec("{artist} - {title}"), accept, one, two);
        assertThat(set.proposals()).extracting(Proposal::status)
                .containsExactly(MatchStatus.AMBIGUOUS_ACCEPTED, MatchStatus.AMBIGUOUS);
        assertThat(set.matched()).isEqualTo(1);
        assertThat(set.ambiguous()).isEqualTo(1);
    }

    @Test
    void a11FixByManualEditKeepsTheStatusUntilAccepted() {
        ScannedFile f = file("Artist - Part - Song.mp3");
        ProposalSet set = run(spec("{artist} - {title}"),
                edits(f, Field.ARTIST, text("Artist - Part"), Field.TITLE, text("Song")), f);
        Proposal p = set.proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.AMBIGUOUS);
        assertThat(p.proposed().get(Field.ARTIST)).contains(text("Artist - Part"));
        assertThat(change(p, Field.ARTIST).orElseThrow().origin()).isEqualTo(ChangeOrigin.MANUAL);
        assertThat(p.exportable()).isFalse();
    }

    @Test
    void e07AcceptAndFixAcDc() {
        ScannedFile f = file("AC - DC - Thunderstruck.mp3");
        ManualEdits fix = new ManualEdits(
                Map.of(f.path(), Map.of(Field.ARTIST, new FieldEdit.Set(text("AC - DC")),
                        Field.TITLE, new FieldEdit.Set(text("Thunderstruck")))), Set.of(f.path()));
        Proposal p = run(spec("{artist} - {title}"), fix, f).proposals().get(0);
        assertThat(p.status()).isEqualTo(MatchStatus.AMBIGUOUS_ACCEPTED);
        assertThat(p.proposed().get(Field.ARTIST)).contains(text("AC - DC"));
        assertThat(p.proposed().get(Field.TITLE)).contains(text("Thunderstruck"));
        assertThat(p.extracted()).containsEntry(Field.ARTIST, "AC").as("extracted keeps the pattern result");
    }

    // ---- A13-A15 ------------------------------------------------------------------------------------------------

    @Test
    void a13FixedValuesForAllMatchedFiles() {
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE,
                fixed(Field.GENRE, genres("Balkan"), Field.ALBUM_ARTIST, text("Various Artists")), null, false);
        ProposalSet set = run(spec, file("A - B.mp3"), file("C - D.mp3"));
        for (Proposal p : set.proposals()) {
            assertThat(p.proposed().get(Field.GENRE)).contains(genres("Balkan"));
            assertThat(p.proposed().get(Field.ALBUM_ARTIST)).contains(text("Various Artists"));
            assertThat(change(p, Field.GENRE).orElseThrow().origin()).isEqualTo(ChangeOrigin.FIXED);
            assertThat(change(p, Field.ALBUM_ARTIST).orElseThrow().origin()).isEqualTo(ChangeOrigin.FIXED);
        }
    }

    @Test
    void a14FixedBeatsPattern() {
        ExtractionSpec spec = spec("{genre} - {title}", MergeMode.OVERWRITE, fixed(Field.GENRE, genres("Y")), null, false);
        Proposal p = run(spec, file("X - Song.mp3")).proposals().get(0);
        assertThat(p.proposed().get(Field.GENRE)).contains(genres("Y"));
        assertThat(change(p, Field.GENRE).orElseThrow().origin()).isEqualTo(ChangeOrigin.FIXED);
        assertThat(p.extracted()).containsEntry(Field.GENRE, "X");
    }

    @Test
    void a15KeepExistingWinsOverPattern() {
        ScannedFile f = file("New - Song.mp3", meta(Field.ARTIST, "Existing"));
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE,
                fixed(Field.ARTIST, new FieldOverride.KeepExisting()), null, false);
        Proposal p = run(spec, f).proposals().get(0);
        assertThat(p.proposed().get(Field.ARTIST)).contains(text("Existing"));
        assertThat(change(p, Field.ARTIST)).isEmpty();
        assertThat(change(p, Field.TITLE)).isPresent();
    }

    @Test
    void a15KeepExistingBeatsFixedToo() {
        ScannedFile f = file("a.mp3", meta(Field.GENRE, genres("Rock")));
        ExtractionSpec spec = spec(null, MergeMode.OVERWRITE, fixed(Field.GENRE, new FieldOverride.KeepExisting()), null,
                false);
        Proposal p = run(spec, f).proposals().get(0);
        assertThat(p.proposed().get(Field.GENRE)).contains(genres("Rock"));
        assertThat(p.changes()).isEmpty();
    }

    @Test
    void a15KeepExistingWithoutExistingValueStaysEmptyAndSaysSo() {
        ScannedFile f = file("New - Song.mp3");
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE,
                fixed(Field.ARTIST, new FieldOverride.KeepExisting()), null, false);
        Proposal p = run(spec, f).proposals().get(0);
        assertThat(p.proposed().get(Field.ARTIST)).isEmpty();
        assertThat(p.issues()).as("INFO KEEP_EMPTY (spec A15)").anySatisfy(issue -> {
            assertThat(issue.code().name()).isEqualTo("KEEP_EMPTY");
            assertThat(issue.severity()).isEqualTo(Severity.INFO);
        });
    }

    // ---- A16 / A17 ----------------------------------------------------------------------------------------------

    @Test
    void a16OverwriteIsTheDefaultAndTheChangeShowsBefore() {
        Proposal p = run(spec("{title}"), file("New.mp3", meta(Field.TITLE, "Old"))).proposals().get(0);
        assertThat(p.proposed().get(Field.TITLE)).contains(text("New"));
        ProposedChange change = change(p, Field.TITLE).orElseThrow();
        assertThat(change.before()).contains(text("Old"));
        assertThat(change.after()).contains(text("New"));
        assertThat(change.origin()).isEqualTo(ChangeOrigin.PATTERN);
    }

    @Test
    void a17FillEmptyNeverOverwrites() {
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.FILL_EMPTY, fixed(Field.GENRE, genres("Pop")), null,
                false);
        ScannedFile full = file("New - NewT.mp3", meta(Field.TITLE, "Old", Field.GENRE, genres("Rock")));
        ScannedFile empty = file("New - NewT.mp3".replace("New - ", "N2 - "));
        ProposalSet set = run(spec, full, empty);
        Proposal a = set.proposals().get(0);
        assertThat(a.proposed().get(Field.TITLE)).contains(text("Old"));
        assertThat(a.proposed().get(Field.GENRE)).contains(genres("Rock"));
        assertThat(a.proposed().get(Field.ARTIST)).contains(text("New")).as("empty field is filled");
        assertThat(change(a, Field.TITLE)).isEmpty();
        assertThat(change(a, Field.GENRE)).isEmpty();
        Proposal b = set.proposals().get(1);
        assertThat(b.proposed().get(Field.TITLE)).contains(text("NewT"));
        assertThat(b.proposed().get(Field.GENRE)).contains(genres("Pop"));
    }

    // ---- A19-A22 ------------------------------------------------------------------------------------------------

    private static final ExtractionSpec NUMBER_BY_NAME = spec("{title}", MergeMode.OVERWRITE, Map.of(),
            new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);

    @Test
    void a19AutoNumberByNameInNaturalOrderPerFolder() {
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 12; i >= 1; i--) {
            files.add(file("Album/Song " + i + ".mp3"));
        }
        files.add(file("Other/b.mp3"));
        files.add(file("Other/a.mp3"));
        ProposalSet set = service.propose(new ProposeRequest(files, NUMBER_BY_NAME, ManualEdits.NONE));
        for (Proposal p : set.proposals()) {
            String name = p.file().path().getFileName().toString();
            Optional<FieldValue> track = p.proposed().get(Field.TRACK);
            if (name.startsWith("Song ")) {
                int n = Integer.parseInt(name.substring(5, name.length() - 4));
                assertThat(track).as(name).contains(pos(n));
            } else {
                assertThat(track).as(name).contains(pos(name.equals("a.mp3") ? 1 : 2));
            }
            assertThat(change(p, Field.TRACK).orElseThrow().origin()).isEqualTo(ChangeOrigin.AUTO_NUMBER);
        }
    }

    @Test
    void a20AutoNumberByExistingTagOrder() {
        ScannedFile t5 = file("a.mp3", meta(Field.TRACK, pos(5)));
        ScannedFile t3 = file("b.mp3", meta(Field.TRACK, pos(3)));
        ScannedFile t9 = file("c.mp3", meta(Field.TRACK, pos(9)));
        ScannedFile none = file("0 first by name.mp3");
        ExtractionSpec spec = spec(null, MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK, false), false);
        ProposalSet set = run(spec, t5, t3, t9, none);
        assertThat(set.proposals().get(0).proposed().get(Field.TRACK)).contains(pos(2));
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(1));
        assertThat(set.proposals().get(2).proposed().get(Field.TRACK)).contains(pos(3));
        assertThat(set.proposals().get(3).proposed().get(Field.TRACK)).contains(pos(4));
    }

    @Test
    void a21TotalIsTheCountInTheFolder() {
        ExtractionSpec with = spec("{title}", MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, true), false);
        ProposalSet set = run(with, file("F1/a.mp3"), file("F1/b.mp3"), file("F1/c.mp3"), file("F2/a.mp3"));
        assertThat(set.proposals()).extracting(p -> p.proposed().get(Field.TRACK).orElseThrow())
                .containsExactly(pos(1, 3), pos(2, 3), pos(3, 3), pos(1, 1));
        ProposalSet without = run(NUMBER_BY_NAME, file("F1/a.mp3"), file("F1/b.mp3"));
        assertThat(without.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(2));
    }

    @Test
    void a22OnlyExportableFilesAreNumbered() {
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, true), false);
        ProposalSet set = run(spec, file("A - one.mp3"), file("nomatch.mp3"), file("P - Q - R.mp3"), file("B - two.mp3"));
        assertThat(set.proposals().get(0).proposed().get(Field.TRACK)).contains(pos(1, 2));
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).isEmpty();
        assertThat(set.proposals().get(2).proposed().get(Field.TRACK)).isEmpty();
        assertThat(set.proposals().get(3).proposed().get(Field.TRACK)).contains(pos(2, 2));
    }

    @Test
    void a22AcceptedAmbiguousFilesAreNumbered() {
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), true);
        ProposalSet set = run(spec, file("A - B - C.mp3"), file("D - E.mp3"));
        assertThat(set.proposals().get(0).proposed().get(Field.TRACK)).contains(pos(1));
        assertThat(set.proposals().get(1).proposed().get(Field.TRACK)).contains(pos(2));
    }

    @Test
    void autoNumberingKeepsExistingTrackWhenKeepIsRequested() {
        ScannedFile f = file("a.mp3", meta(Field.TRACK, pos(8)));
        ExtractionSpec spec = spec(null, MergeMode.OVERWRITE, fixed(Field.TRACK, new FieldOverride.KeepExisting()),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        assertThat(run(spec, f).proposals().get(0).proposed().get(Field.TRACK)).contains(pos(8));
    }

    // ---- A24 / A25 ----------------------------------------------------------------------------------------------

    @Test
    void a24ChangeSetHasOriginsAndIsDeterministic() {
        ScannedFile f = file("Art - Song.mp3", meta(Field.ARTIST, "Old", Field.COMMENT, "same"));
        ExtractionSpec spec = spec("{artist} - {title}", MergeMode.OVERWRITE, fixed(Field.GENRE, genres("Pop")),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        ManualEdits manual = edits(f, Field.ALBUM, text("Manual Album"));
        ProposalSet first = run(spec, manual, f);
        Proposal p = first.proposals().get(0);
        assertThat(p.changes()).extracting(ProposedChange::field)
                .containsExactly(Field.TITLE, Field.ARTIST, Field.ALBUM, Field.TRACK, Field.GENRE);
        assertThat(p.changes()).extracting(ProposedChange::origin).containsExactly(ChangeOrigin.PATTERN,
                ChangeOrigin.PATTERN, ChangeOrigin.MANUAL, ChangeOrigin.AUTO_NUMBER, ChangeOrigin.FIXED);
        assertThat(p.existing().differingFields(p.proposed())).containsExactlyInAnyOrder(Field.TITLE, Field.ARTIST,
                Field.ALBUM, Field.TRACK, Field.GENRE);
        assertThat(run(spec, manual, f)).isEqualTo(first);
    }

    @Test
    void a25PrecedenceExistingPatternFixedManual() {
        ScannedFile f = file("Pat.mp3", meta(Field.TITLE, "Existing"));
        assertThat(run(spec(null), f).proposals().get(0).proposed().get(Field.TITLE)).contains(text("Existing"));
        assertThat(run(spec("{title}"), f).proposals().get(0).proposed().get(Field.TITLE)).contains(text("Pat"));
        ExtractionSpec withFixed = spec("{title}", MergeMode.OVERWRITE, fixed(Field.TITLE, text("Fixed")), null, false);
        assertThat(run(withFixed, f).proposals().get(0).proposed().get(Field.TITLE)).contains(text("Fixed"));
        Proposal manual = run(withFixed, edits(f, Field.TITLE, text("Manual")), f).proposals().get(0);
        assertThat(manual.proposed().get(Field.TITLE)).contains(text("Manual"));
        assertThat(change(manual, Field.TITLE).orElseThrow().origin()).isEqualTo(ChangeOrigin.MANUAL);
        assertThat(change(manual, Field.TITLE).orElseThrow().before()).contains(text("Existing"));
    }

    @Test
    void a25AutoNumberBeatsExistingAndManualBeatsAutoNumber() {
        ScannedFile f = file("a.mp3", meta(Field.TRACK, pos(9)));
        ExtractionSpec numbering = spec(null, MergeMode.OVERWRITE, Map.of(),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        assertThat(run(numbering, f).proposals().get(0).proposed().get(Field.TRACK)).contains(pos(1));
        assertThat(run(numbering, edits(f, Field.TRACK, pos(5)), f).proposals().get(0).proposed().get(Field.TRACK))
                .contains(pos(5));
    }

    @Test
    void a25FixedTrackBeatsAutoNumber() {
        ExtractionSpec spec = spec(null, MergeMode.OVERWRITE, fixed(Field.TRACK, pos(4)),
                new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false), false);
        assertThat(run(spec, file("a.mp3"), file("b.mp3")).proposals()).extracting(p -> p.proposed().get(Field.TRACK).get())
                .containsExactly(pos(4), pos(4));
    }

    @Test
    void a25ManualClearRemovesTheValue() {
        ScannedFile f = file("Pat.mp3", meta(Field.TITLE, "Existing", Field.COMMENT, "c"));
        Proposal p = run(spec("{title}"), edits(f, Field.COMMENT, new FieldEdit.Clear()), f).proposals().get(0);
        assertThat(p.proposed().get(Field.COMMENT)).isEmpty();
        ProposedChange change = change(p, Field.COMMENT).orElseThrow();
        assertThat(change.before()).contains(text("c"));
        assertThat(change.after()).isEmpty();
        assertThat(change.origin()).isEqualTo(ChangeOrigin.MANUAL);
    }

    @Test
    void manualEditsForOtherFilesAreIgnored() {
        ScannedFile a = file("a.mp3");
        ScannedFile b = file("b.mp3");
        ProposalSet set = run(spec(null), edits(a, Field.TITLE, text("Only A")), a, b);
        assertThat(set.proposals().get(0).proposed().get(Field.TITLE)).contains(text("Only A"));
        assertThat(set.proposals().get(1).proposed().get(Field.TITLE)).isEmpty();
    }

    // ---- E20-E22 ------------------------------------------------------------------------------------------------

    @Test
    void e20ConflictingTagsUseTheEffectiveValuesAndCarryAnInfo() {
        ScannedFile f = conflicting("Pat.mp3", meta(Field.ARTIST, "V1 Artist"), meta(Field.ARTIST, "V2 Artist"));
        assertThat(f.conflicts()).isNotEmpty();
        Proposal p = run(spec("{title}"), f).proposals().get(0);
        assertThat(p.existing().get(Field.ARTIST)).contains(text("V2 Artist"));
        assertThat(p.proposed().get(Field.ARTIST)).contains(text("V2 Artist"));
        assertThat(p.issues()).anySatisfy(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.V1_V2_CONFLICT);
            assertThat(i.severity()).isEqualTo(Severity.INFO);
            assertThat(i.file()).contains(f.path());
        });
    }

    @Test
    void e21DuplicateTargetNamesAreNotDeduplicated() {
        ProposalSet set = run(spec("{artist} - {title}"), file("F1/A - B.mp3"), file("F2/A - B.mp3"));
        assertThat(set.proposals()).hasSize(2);
        assertThat(set.proposals()).allSatisfy(p -> assertThat(p.status()).isEqualTo(MatchStatus.MATCHED));
        assertThat(set.proposals().get(0).proposed()).isEqualTo(set.proposals().get(1).proposed());
    }

    @Test
    void e22EmptyAndSingleFileLists() {
        ProposalSet empty = run(spec("{title}"));
        assertThat(empty.proposals()).isEmpty();
        assertThat(empty.matched() + empty.ambiguous() + empty.noMatch()).isZero();
        ProposalSet one = run(spec("{title}"), file("only.mp3"));
        assertThat(one.proposals()).hasSize(1);
        assertThat(one.matched()).isEqualTo(1);
    }

    @Test
    void unrelatedExistingFieldsAreCopiedUnchanged() {
        ScannedFile f = file("Art - Song.mp3", meta(Field.COMMENT, "keep me", Field.GENRE, genres("Pop", "Rock"),
                Field.YEAR, new FieldValue.YearValue(1990), Field.ALBUM, "Ђорђе"));
        Proposal p = run(spec("{artist} - {title}"), f).proposals().get(0);
        for (Field field : List.of(Field.COMMENT, Field.GENRE, Field.YEAR, Field.ALBUM)) {
            assertThat(p.proposed().get(field)).as(field.name()).isEqualTo(p.existing().get(field));
        }
    }

    // ---- D03 / D04 / D07 / X05 ----------------------------------------------------------------------------------

    @Test
    void d03PropertyOnlyCleanStatusesAreExportable() {
        Random random = new Random(7);
        String[] atoms = {"A", "B", " - ", " - ", "_", "01", "2020", "x", "Ђ", " "};
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = random.nextInt(7); k >= 0; k--) {
                sb.append(atoms[random.nextInt(atoms.length)]);
            }
            files.add(file("f" + i + "/" + sb + ".mp3"));
        }
        for (boolean accept : new boolean[] {false, true}) {
            ProposalSet set = service.propose(new ProposeRequest(files,
                    spec("{artist} - {title}", MergeMode.OVERWRITE, Map.of(), null, accept), ManualEdits.NONE));
            for (Proposal p : set.proposals()) {
                assertThat(p.exportable()).isEqualTo(Set.of(MatchStatus.NO_PATTERN, MatchStatus.MATCHED,
                        MatchStatus.AMBIGUOUS_ACCEPTED).contains(p.status()));
                if (p.status() == MatchStatus.NO_MATCH || p.status() == MatchStatus.AMBIGUOUS) {
                    assertThat(p.exportable()).isFalse();
                }
                if (p.status() == MatchStatus.NO_MATCH) {
                    assertThat(p.changes()).isEmpty();
                }
            }
            assertThat(set.matched() + set.ambiguous() + set.noMatch()).isEqualTo(set.proposals().size());
        }
    }

    @Test
    void d04ExistingValuesAreNeverDroppedSilently() {
        Random random = new Random(11);
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<Field, FieldValue> existing = new EnumMap<>(Field.class);
            if (random.nextBoolean()) {
                existing.put(Field.TITLE, text("Old " + i));
            }
            if (random.nextBoolean()) {
                existing.put(Field.ARTIST, text("Old Artist " + i));
            }
            if (random.nextBoolean()) {
                existing.put(Field.COMMENT, text("comment " + i));
            }
            if (random.nextBoolean()) {
                existing.put(Field.GENRE, genres("Rock"));
            }
            files.add(file("d/New " + i + " - Song " + i + ".mp3", new MediaMetadata(existing)));
        }
        for (MergeMode mode : MergeMode.values()) {
            ExtractionSpec spec = spec("{artist} - {title}", mode, fixed(Field.GENRE, genres("Pop")), null, false);
            ProposalSet set = service.propose(new ProposeRequest(files, spec, ManualEdits.NONE));
            for (Proposal p : set.proposals()) {
                for (Field field : Field.values()) {
                    Optional<FieldValue> before = p.existing().get(field);
                    Optional<FieldValue> after = p.proposed().get(field);
                    if (before.isPresent() && !after.equals(before)) {
                        assertThat(change(p, field)).as("%s %s", mode, field).isPresent();
                        assertThat(change(p, field).get().before()).isEqualTo(before);
                    }
                    if (mode == MergeMode.FILL_EMPTY && before.isPresent()) {
                        assertThat(after).as("fill-empty must not overwrite %s", field).isEqualTo(before);
                    }
                    if (!List.of(Field.TITLE, Field.ARTIST, Field.GENRE).contains(field)) {
                        assertThat(after).as("untouched %s", field).isEqualTo(before);
                    }
                }
            }
        }
    }

    @Test
    void d07TheProposalCarriesTheScannedFileForStaleChecks() {
        ScannedFile f = file("A - B.mp3");
        Proposal p = run(spec("{artist} - {title}"), f).proposals().get(0);
        assertThat(p.file()).isSameAs(f);
        assertThat(p.file().fingerprint()).isEqualTo(f.fingerprint());
    }

    @Test
    void x05CountsAreConsistentAndNoPatternIsCountedSeparately() {
        ProposalSet noPattern = run(spec(null, MergeMode.OVERWRITE, fixed(Field.GENRE, genres("Pop")), null, false),
                file("a.mp3"), file("b.mp3"));
        assertThat(noPattern.proposals()).hasSize(2).allSatisfy(p -> assertThat(p.status()).isEqualTo(MatchStatus.NO_PATTERN));
        assertThat(noPattern.matched() + noPattern.ambiguous() + noPattern.noMatch()).isZero();

        ProposalSet mixed = run(spec("{artist} - {title}"), file("A - B.mp3"), file("x.mp3"), file("A - B - C.mp3"));
        assertThat(mixed.matched() + mixed.ambiguous() + mixed.noMatch()).isLessThanOrEqualTo(mixed.proposals().size());
        assertThat(mixed.matched()).isEqualTo(1);
        assertThat(mixed.ambiguous()).isEqualTo(1);
        assertThat(mixed.noMatch()).isEqualTo(1);
    }

    @Test
    void theStoryExamplesRunThroughTheService() {
        ExtractionSpec spec = spec("{album}/{artist}_{title}");
        Proposal p = run(spec, file("Album/Artist_Song.mp3")).proposals().get(0);
        assertThat(p.extracted()).containsEntry(Field.ALBUM, "Album").containsEntry(Field.ARTIST, "Artist");
    }

    // ---- M17 ----------------------------------------------------------------------------------------------------

    @Test
    @Tag("slow")
    void m17FiveThousandFilesStayFast() {
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            files.add(file("Album " + (i / 100) + "/" + i + " - Artist " + i + " - Song Name " + i + " - " + (1950 + i % 70) + ".mp3"));
        }
        ProposeRequest request = new ProposeRequest(files, spec("{album}/{track} - {artist} - {title} - {year}"),
                ManualEdits.NONE);
        Assertions.assertTimeout(Duration.ofSeconds(5), () -> {
            ProposalSet set = service.propose(request);
            assertThat(set.proposals()).hasSize(5_000);
            assertThat(set.matched() + set.ambiguous()).isEqualTo(5_000);
        });
    }

    // quiet unused-import guard
    @SuppressWarnings("unused")
    private static final Class<?>[] USED = {Issue.class, Path.class};
}
