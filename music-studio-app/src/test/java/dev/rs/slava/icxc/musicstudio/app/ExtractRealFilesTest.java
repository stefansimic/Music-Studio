package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.Snapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scan -> propose with the real adapters on copied fixtures (A23, D01, D06, X06, E09-E11, E13, E23). Nothing outside the
 * temp directory is touched; the sources stay byte-identical, with the same mtime and permissions.
 */
class ExtractRealFilesTest {

    @TempDir
    Path tempDir;

    private Path in;
    private MusicStudio studio;

    @BeforeEach
    void setUp() throws IOException {
        in = Files.createDirectories(tempDir.resolve("in"));
        studio = new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"), Clock.systemUTC()))
                .create();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private Path copy(Fixture fixture, String relative) throws IOException {
        Path target = in.resolve(relative);
        Files.createDirectories(target.getParent());
        return Fixtures.copyTo(fixture, target.getParent(), target.getFileName().toString());
    }

    private List<ScannedFile> scan() {
        List<ScanItem> items = new ArrayList<>();
        studio.scan().scan(new ScanRequest(in, true, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.LENIENT),
                items::add, ProgressListener.NONE, Cancellation.NONE);
        return items.stream().filter(ScannedFile.class::isInstance).map(ScannedFile.class::cast)
                .sorted((a, b) -> a.path().compareTo(b.path())).toList();
    }

    private static InputPattern pattern(String source) {
        return ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(source)).pattern();
    }

    private ProposalSet propose(List<ScannedFile> files, ExtractionSpec spec) {
        return studio.proposeMetadata().propose(new ProposeRequest(files, spec, ManualEdits.NONE));
    }

    private Proposal only(String pattern) {
        List<ScannedFile> files = scan();
        assertThat(files).hasSize(1);
        return propose(files, ExtractionSpec.of(pattern(pattern))).proposals().get(0);
    }

    /** Everything observable about the tree: file snapshots (hash, size, mtime, permissions) and directory state. */
    private Map<String, Object> state() throws IOException {
        Map<String, Object> state = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(tempDir)) {
            for (Path path : walk.toList()) {
                String key = tempDir.relativize(path).toString();
                if (Files.isDirectory(path)) {
                    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
                    List<String> listing;
                    try (Stream<Path> list = Files.list(path)) {
                        listing = list.map(p -> p.getFileName().toString()).sorted().toList();
                    }
                    state.put(key + "/", List.of(listing, perms, Files.getLastModifiedTime(path).toMillis()));
                } else {
                    state.put(key, RoundTripSupport.snapshot(path));
                }
            }
        }
        return state;
    }

    // ---- D06 / X06 ----------------------------------------------------------------------------------------------

    @Test
    void d06TestsWorkInsideTheTemporaryDirectoryOnly() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        assertThat(tempDir.toRealPath()).startsWith(tmp);
        assertThat(in.toRealPath()).startsWith(tempDir.toRealPath());
    }

    @Test
    void x06FactoryYieldsAWorkingProposeUseCaseOverThreeCopiedFixtures() throws IOException {
        copy(Fixture.V23_LATIN1, "Album/Artist A - Song A.mp3");
        copy(Fixture.V1_1, "Album/Artist B - Song B.mp3");
        copy(Fixture.NO_TAGS, "Album/Artist C - Song C.mp3");
        List<ScannedFile> files = scan();
        assertThat(files).hasSize(3);
        ProposalSet set = propose(files, ExtractionSpec.of(pattern("{album}/{artist} - {title}")));
        assertThat(set.matched()).isEqualTo(3);
        assertThat(set.proposals()).allSatisfy(p -> {
            assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
            assertThat(p.proposed().get(Field.ALBUM)).contains(new FieldValue.Text("Album"));
            assertThat(p.existing()).isEqualTo(p.file().tags().effective());
        });
        assertThat(set.proposals()).extracting(p -> p.proposed().display(Field.TITLE).orElseThrow())
                .containsExactly("Song A", "Song B", "Song C");
    }

    // ---- A23 / D01 ----------------------------------------------------------------------------------------------

    @Test
    void a23And01ProposingWritesNothingNotEvenForNoMatchAndAmbiguousFiles() throws IOException {
        copy(Fixture.V23_LATIN1, "Best Of 2020/Artist - Song - 2020.mp3");
        copy(Fixture.V23_UTF16_CYRILLIC, "Best Of 2020/Ђорђе - Живот.mp3");
        copy(Fixture.V24_UTF8, "Best Of 2020/Artist - Part - Song - 2019.mp3");
        copy(Fixture.V1_0, "Best Of 2020/no separator here.mp3");
        copy(Fixture.V23_COVER, "Other/01.mp3");
        copy(Fixture.V23_EXTRAS, "Other/02.mp3");
        Files.writeString(in.resolve("Best Of 2020/cover.jpg"), "not really a jpeg");
        Map<String, Object> before = state();

        List<ScannedFile> files = scan();
        assertThat(state()).as("scan alone changes nothing").isEqualTo(before);

        Map<Field, FieldOverride> fixed = Map.of(Field.GENRE,
                new FieldOverride.Fixed(new FieldValue.Genres(List.of(new Genre("Pop")))));
        for (boolean accept : new boolean[] {false, true}) {
            ProposalSet set = propose(files, new ExtractionSpec(Optional.of(pattern("{album}/{artist} - {title} - {year}")),
                    fixed, MergeMode.OVERWRITE,
                    Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, true)), accept));
            assertThat(set.proposals()).hasSize(files.size());
            assertThat(set.noMatch()).isGreaterThan(0);
        }
        propose(files, ExtractionSpec.of(pattern("{track}"))); // another one that touches the "Other" folder
        assertThat(state()).as("propose changes nothing: bytes, mtime, permissions, directory listings")
                .isEqualTo(before);
    }

    @Test
    void existingTagsOfTheFilesAreVisibleAsBeforeInTheChangeSet() throws IOException {
        copy(Fixture.V23_UTF16_CYRILLIC, "Alb/Нова Песма - Наслов.mp3");
        ScannedFile file = scan().get(0);
        assertThat(file.tags().effective().isEmpty()).isFalse();
        Proposal p = propose(List.of(file), ExtractionSpec.of(pattern("{artist} - {title}"))).proposals().get(0);
        assertThat(p.existing()).isEqualTo(file.tags().effective());
        for (var change : p.changes()) {
            assertThat(change.before()).isEqualTo(file.tags().effective().get(change.field()));
        }
        for (Field field : Field.values()) {
            if (field != Field.ARTIST && field != Field.TITLE) {
                assertThat(p.proposed().get(field)).as(field.name()).isEqualTo(p.existing().get(field));
            }
        }
        assertThat(p.proposed().get(Field.ARTIST)).contains(new FieldValue.Text("Нова Песма"));
    }

    // ---- E09-E11, E13, E23 --------------------------------------------------------------------------------------

    @Test
    void e09GreekNamesOnDisk() throws IOException {
        copy(Fixture.V23_UTF16_GREEK, "Ελευθερία Αρβανιτάκη/Μίκης Θεοδωράκης - Ένα το χελιδόνι.mp3");
        Proposal p = only("{albumartist}/{artist} - {title}");
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.extracted()).containsEntry(Field.ALBUM_ARTIST, "Ελευθερία Αρβανιτάκη")
                .containsEntry(Field.ARTIST, "Μίκης Θεοδωράκης").containsEntry(Field.TITLE, "Ένα το χελιδόνι");
    }

    @Test
    void e09GreekLiteralSeparatorAndSigmas() throws IOException {
        copy(Fixture.NO_TAGS, "Σωκράτης και Ὀδυσσεύς ΣΑΣ ς.mp3");
        Proposal p = only("{artist} και {title}");
        assertThat(p.extracted()).containsEntry(Field.ARTIST, "Σωκράτης").containsEntry(Field.TITLE, "Ὀδυσσεύς ΣΑΣ ς");
    }

    @Test
    void e10SerbianCyrillicWithEnDash() throws IOException {
        copy(Fixture.V24_UTF8_GREEK_CYRILLIC, "Теразије/Ђорђе Балашевић – Живот је море.mp3");
        Proposal p = only("{album}/{artist} – {title}");
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.extracted()).containsEntry(Field.ALBUM, "Теразије").containsEntry(Field.ARTIST, "Ђорђе Балашевић")
                .containsEntry(Field.TITLE, "Живот је море");
    }

    @Test
    void e10HyphenPatternDoesNotMatchEnDashNameAndExplainsWhy() throws IOException {
        copy(Fixture.NO_TAGS, "Ђорђе Балашевић – Живот.mp3");
        Proposal p = only("{artist} - {title}");
        assertThat(p.status()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(p.matchDetail().orElseThrow()).containsIgnoringCase("dash");
    }

    @Test
    void e11LatinDiacritics() throws IOException {
        copy(Fixture.NO_TAGS, "Đorđe Balašević - Ćevapi, Šljivovica.mp3");
        Proposal p = only("{artist} - {title}");
        assertThat(p.extracted()).containsEntry(Field.ARTIST, "Đorđe Balašević")
                .containsEntry(Field.TITLE, "Ćevapi, Šljivovica");
    }

    @Test
    void e12NfdFileNameOnDiskGivesNfcValues() throws IOException {
        String nfd = Normalizer.normalize("čevapi - Đorđe", Normalizer.Form.NFD);
        copy(Fixture.NO_TAGS, nfd + ".mp3");
        Proposal p = only("{artist} - {title}");
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.proposed().get(Field.ARTIST)).contains(new FieldValue.Text("čevapi"));
        assertThat(p.extracted().get(Field.ARTIST)).isEqualTo(Normalizer.normalize(p.extracted().get(Field.ARTIST),
                Normalizer.Form.NFC));
    }

    @Test
    void e13EmojiOnDisk() throws IOException {
        copy(Fixture.NO_TAGS, "🎵 Artist - Song 🎶.mp3");
        Proposal p = only("🎵 {artist} - {title} 🎶");
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.extracted()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.TITLE, "Song");
        Proposal inCapture = only("{artist} - {title}");
        assertThat(inCapture.extracted()).containsEntry(Field.ARTIST, "🎵 Artist").containsEntry(Field.TITLE, "Song 🎶");
    }

    @Test
    void e23NonAsciiFileNameThroughScanAndPropose() throws IOException {
        copy(Fixture.V23_LATIN1, "Ђорђе – 🎵 ČĆŠŽĐ.mp3");
        Proposal p = only("{artist} – {title}");
        assertThat(p.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(p.proposed().get(Field.ARTIST)).contains(new FieldValue.Text("Ђорђе"));
        assertThat(p.proposed().get(Field.TITLE)).contains(new FieldValue.Text("🎵 ČĆŠŽĐ"));
        assertThat(p.file().path().getFileName().toString()).isEqualTo("Ђорђе – 🎵 ČĆŠŽĐ.mp3");
    }

    @Test
    void autoNumberingOnRealFilesUsesThePerFolderOrder() throws IOException {
        copy(Fixture.NO_TAGS, "A/b.mp3");
        copy(Fixture.NO_TAGS, "A/a.mp3");
        copy(Fixture.NO_TAGS, "B/z.mp3");
        ExtractionSpec spec = new ExtractionSpec(Optional.of(pattern("{title}")), Map.of(), MergeMode.OVERWRITE,
                Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, true)), false);
        ProposalSet set = propose(scan(), spec);
        Map<String, String> tracks = new TreeMap<>();
        set.proposals().forEach(p -> tracks.put(in.relativize(p.file().path()).toString(),
                p.proposed().display(Field.TRACK).orElse("-")));
        assertThat(tracks).containsEntry("A/a.mp3", "1/2").containsEntry("A/b.mp3", "2/2")
                .containsEntry("B/z.mp3", "1/1");
    }
}
