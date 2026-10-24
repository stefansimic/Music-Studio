package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Story 002 on real MP3 files with real-world names through the real wiring (tester additions): every naming scheme
 * from the edge-case list, non-ASCII incl. NFD names as macOS writes them, emoji – and the guarantee that
 * extraction writes nothing (content, mtime, permissions, directory mtimes, no new entries).
 */
class ExtractRealWorldEndToEndTest {

    private static final Pattern EXTRACTED = Pattern.compile("\"extracted\":(\\{[^{}]*\\})");
    private static final Pattern STATUS = Pattern.compile("\"status\":\"([a-z_]+)\"");

    @TempDir
    Path tempDir;

    private Path music;
    private Path schemeDash;
    private Path schemeDot;
    private Path schemeUnderscore;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private static String nfd(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD);
    }

    @BeforeEach
    void files() {
        music = tempDir.toAbsolutePath().resolve("music");
        // "01 - " leading tracks, folder with year, extra/missing spaces, double separator, emoji, NFD
        schemeDash = music.resolve("2020 - Best Of");
        Fixtures.copyTo(Fixture.V23_LATIN1, schemeDash, "01 - Artist - Song.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, schemeDash, "02 - Jay-Z - Hip-Hop Track.mp3");
        Fixtures.copyTo(Fixture.V24_UTF8, schemeDash, "03 -Artist-Missing Spaces.mp3");
        Fixtures.copyTo(Fixture.V1_1, schemeDash, "04  -  Die Ärzte  -  Lied (2020).mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, schemeDash, "05 - A - B - C.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, schemeDash, "06 - 🔥 Fire - Song 🎶.mp3");
        Fixtures.copyTo(Fixture.V23_UTF16_CYRILLIC, schemeDash, nfd("07 - Đorđe Balašević - Računajte na nas.mp3"));
        Fixtures.copyTo(Fixture.NO_TAGS, schemeDash, "Bad Name.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, schemeDash, "08 - Artist -- Double.mp3");
        // "01. " leading tracks, year in round brackets, Cyrillic
        schemeDot = music.resolve("Ђурђевдан (1986)");
        Fixtures.copyTo(Fixture.V23_UTF16_CYRILLIC, schemeDot, "01. Бијело Дугме - Ђурђевдан.mp3");
        Fixtures.copyTo(Fixture.V22, schemeDot, "02.Artist - Song.mp3");
        // "01_" leading tracks, year in square brackets, CJK
        schemeUnderscore = music.resolve("[2019] 坂本龍一");
        Fixtures.copyTo(Fixture.V23_COVER, schemeUnderscore, "01_Sakamoto_Merry Christmas.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, schemeUnderscore, "02_Artist_Song_Name.mp3");
    }

    private int run(String... args) {
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
        AppConfig config = new AppConfig(tempDir.resolve("config").toAbsolutePath(),
                tempDir.resolve("data").toAbsolutePath(), Clock.systemUTC());
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudioFactory(config).create(),
                Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    /** JSON item lines by their NFC-normalised path. */
    private Map<String, String> items() {
        Map<String, String> items = new TreeMap<>();
        out.toString().lines().filter(line -> line.startsWith("{\"path\":")).forEach(line -> {
            String path = line.substring("{\"path\":\"".length(), line.indexOf("\",\"status\""));
            items.put(Normalizer.normalize(path, Normalizer.Form.NFC), line);
        });
        return items;
    }

    private static String extracted(String item) {
        Matcher matcher = EXTRACTED.matcher(item);
        assertThat(matcher.find()).as("extracted in %s", item).isTrue();
        return matcher.group(1);
    }

    private static String status(String item) {
        Matcher matcher = STATUS.matcher(item);
        assertThat(matcher.find()).as("status in %s", item).isTrue();
        return matcher.group(1);
    }

    /**
     * Every entry below the temp dir: type, size, SHA-256, mtime and POSIX permissions of files; mtime and
     * permissions of directories (a created-then-deleted temp file would change the directory mtime).
     */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(tempDir)) {
            for (Path path : paths.toList()) {
                String common = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS) + " "
                        + PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS));
                snapshot.put(tempDir.relativize(path).toString(), Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        ? "file " + Files.size(path) + " " + Fixtures.sha256(path) + " " + common
                        : "dir " + common);
            }
        }
        return snapshot;
    }

    @Test
    void leadingTrackDashFolderYearSpacesEmojiAndNfd() {
        int code = run("extract", schemeDash.toString(), "--pattern", "{year} - {album}/{track} - {artist} - {title}",
                "--format", "json");
        assertThat(code).as("ambiguous + no match → item errors; stderr %s", err).isEqualTo(ExitCode.ITEM_ERRORS);
        Map<String, String> items = items();
        assertThat(items).hasSize(9);

        assertThat(status(items.get("01 - Artist - Song.mp3"))).isEqualTo("matched");
        assertThat(extracted(items.get("01 - Artist - Song.mp3"))).contains("\"title\":\"Song\"")
                .contains("\"artist\":\"Artist\"").contains("\"album\":\"Best Of\"").contains("\"year\":\"2020\"")
                .contains("\"track\":\"01\"");

        assertThat(status(items.get("02 - Jay-Z - Hip-Hop Track.mp3"))).isEqualTo("matched");
        assertThat(extracted(items.get("02 - Jay-Z - Hip-Hop Track.mp3"))).contains("\"artist\":\"Jay-Z\"")
                .contains("\"title\":\"Hip-Hop Track\"");

        assertThat(status(items.get("03 -Artist-Missing Spaces.mp3"))).isEqualTo("matched");
        assertThat(extracted(items.get("03 -Artist-Missing Spaces.mp3"))).contains("\"artist\":\"Artist\"")
                .contains("\"title\":\"Missing Spaces\"").contains("\"track\":\"03\"");

        String extra = items.get("04  -  Die Ärzte  -  Lied (2020).mp3");
        assertThat(status(extra)).isEqualTo("matched");
        assertThat(extracted(extra)).contains("\"artist\":\"Die Ärzte\"").contains("\"title\":\"Lied (2020)\"");

        String ambiguous = items.get("05 - A - B - C.mp3");
        assertThat(status(ambiguous)).isEqualTo("ambiguous");
        assertThat(extracted(ambiguous)).contains("\"artist\":\"A\"").contains("\"title\":\"B - C\"");
        assertThat(ambiguous).containsPattern("\"alternative\":\\{[^}]*\"artist\":\"A - B\"")
                .containsPattern("\"alternative\":\\{[^}]*\"title\":\"C\"");

        String emoji = items.get("06 - 🔥 Fire - Song 🎶.mp3");
        assertThat(status(emoji)).isEqualTo("matched");
        assertThat(extracted(emoji)).contains("\"artist\":\"🔥 Fire\"").contains("\"title\":\"Song 🎶\"");

        try (Stream<Path> list = Files.list(schemeDash)) {
            assertThat(list.map(f -> f.getFileName().toString()))
                    .as("the fixture name is really stored decomposed (NFD) on disk")
                    .anySatisfy(n -> assertThat(Normalizer.isNormalized(n, Normalizer.Form.NFC)).isFalse());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        String nfdItem = items.get("07 - Đorđe Balašević - Računajte na nas.mp3");
        assertThat(nfdItem).as("NFD file is listed").isNotNull();
        assertThat(status(nfdItem)).isEqualTo("matched");
        // values are NFC (exact string, not normalised by the test)
        assertThat(extracted(nfdItem)).contains("\"artist\":\"Đorđe Balašević\"")
                .contains("\"title\":\"Računajte na nas\"");
        assertThat(nfdItem).contains("\"proposed\":{\"title\":\"Računajte na nas\",\"artist\":\"Đorđe Balašević\"");

        assertThat(status(items.get("Bad Name.mp3"))).isEqualTo("no_match");
        assertThat(items.get("Bad Name.mp3")).contains("\"changes\":[]");

        assertThat(status(items.get("08 - Artist -- Double.mp3"))).isEqualTo("ambiguous");
    }

    @Test
    void leadingTrackDotAndYearInRoundBrackets() {
        int code = run("extract", schemeDot.toString(), "--pattern", "{album} ({year})/{track}. {artist} - {title}",
                "--format", "json");
        assertThat(code).as("stderr %s", err).isEqualTo(ExitCode.OK);
        Map<String, String> items = items();
        assertThat(extracted(items.get("01. Бијело Дугме - Ђурђевдан.mp3"))).contains("\"album\":\"Ђурђевдан\"")
                .contains("\"year\":\"1986\"").contains("\"track\":\"01\"").contains("\"artist\":\"Бијело Дугме\"")
                .contains("\"title\":\"Ђурђевдан\"");
        assertThat(extracted(items.get("02.Artist - Song.mp3"))).contains("\"track\":\"02\"")
                .contains("\"artist\":\"Artist\"").contains("\"title\":\"Song\"");
        assertThat(items.values()).allSatisfy(item -> assertThat(status(item)).isEqualTo("matched"));
    }

    @Test
    void leadingTrackUnderscoreAndYearInSquareBrackets() {
        int code = run("extract", schemeUnderscore.toString(), "--pattern", "{year} {album}/{track}_{artist}_{title}",
                "--format", "json");
        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
        Map<String, String> items = items();
        String first = items.get("01_Sakamoto_Merry Christmas.mp3");
        assertThat(status(first)).isEqualTo("matched");
        assertThat(extracted(first)).contains("\"year\":\"[2019]\"").contains("\"album\":\"坂本龍一\"")
                .contains("\"artist\":\"Sakamoto\"").contains("\"title\":\"Merry Christmas\"");
        assertThat(first).contains("\"year\":2019");
        assertThat(status(items.get("02_Artist_Song_Name.mp3"))).isEqualTo("ambiguous");

        int accepted = run("extract", schemeUnderscore.toString(), "--pattern",
                "{year} {album}/{track}_{artist}_{title}", "--accept-ambiguous", "--format", "json");
        assertThat(accepted).as("accepted ambiguity is no item error").isEqualTo(ExitCode.OK);
        String acceptedItem = items().get("02_Artist_Song_Name.mp3");
        assertThat(status(acceptedItem)).isEqualTo("ambiguous_accepted");
        assertThat(extracted(acceptedItem)).contains("\"artist\":\"Artist\"").contains("\"title\":\"Song_Name\"");
    }

    @Test
    void tableShowsNoMatchAndAmbiguousRowsHighlighted() {
        int code = run("extract", schemeDash.toString(), "--pattern", "{year} - {album}/{track} - {artist} - {title}",
                "--set", "genre=Pop", "--set", "albumartist=Various Artists");
        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
        String table = out.toString();
        assertThat(table.lines().filter(l -> l.startsWith("Bad Name.mp3"))).singleElement()
                .satisfies(l -> assertThat(l).contains("NO MATCH"));
        assertThat(table.lines().filter(l -> l.startsWith("05 - A - B - C.mp3"))).singleElement()
                .satisfies(l -> assertThat(l).contains("AMBIGUOUS").contains("also possible"));
        assertThat(table).contains("9 files: 6 matched, 2 ambiguous").contains("1 no match")
                .endsWith("Preview only – no file was changed." + System.lineSeparator());
    }

    @Test
    void quotesBackslashesAndFat32IllegalCharactersInNamesAreExtractedAndEscaped() {
        Path odd = music.resolve("Odd");
        Fixtures.copyTo(Fixture.NO_TAGS, odd, "Artist - Say \"Hello\".mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, odd, "Who? - What: *Now* <1> | back\\slash.mp3");
        int code = run("extract", odd.toString(), "--pattern", "{artist} - {title}", "--format", "json");
        assertThat(code).as("stderr %s", err).isEqualTo(ExitCode.OK);
        Map<String, String> items = items();
        assertThat(items).hasSize(2);
        assertThat(items.values()).anySatisfy(item -> assertThat(extracted(item))
                .contains("\"title\":\"Say \\\"Hello\\\"\""));
        assertThat(items.values()).anySatisfy(item -> assertThat(extracted(item)).contains("\"artist\":\"Who?\"")
                .contains("\"title\":\"What: *Now* <1> | back\\\\slash\""));
    }

    @Test
    void fixedValueGivenInNfdIsShownInNfc() {
        run("extract", schemeDot.toString(), "--set", "albumartist=" + nfd("Razni izvođači"), "--format", "json");
        assertThat(items().values()).allSatisfy(item ->
                assertThat(item).contains("\"albumartist\":\"Razni izvođači\""));
    }

    @Test
    void extractionWritesNothingWithAnyOptionCombination() throws IOException {
        Fixtures.copyTo(Fixture.V23_LATIN1, music, ".Hidden - File - 2000.mp3");
        Fixtures.copyTo(Fixture.CORRUPT, music, "Corrupt - File - 2000.mp3");
        Fixtures.copyTo(Fixture.EMPTY, music, "Empty - File - 2000.mp3");
        Map<String, String> before = snapshot();
        String root = music.toString();
        List<String[]> runs = new ArrayList<>();
        runs.add(new String[] {"extract", root, "-r", "--pattern", "{album}/{artist} - {title}"});
        runs.add(new String[] {"extract", root, "-r", "--include-hidden", "--pattern", "{album}/{artist} - {title}",
                "--format", "json"});
        runs.add(new String[] {"extract", root, "-r", "--pattern", "{*} - {artist} - {title}", "--set", "genre=Pop",
                "--set", "year=2020", "--keep", "album", "--merge", "fill-empty", "--auto-number", "tag",
                "--track-total", "--accept-ambiguous"});
        runs.add(new String[] {"extract", root, "-r", "--auto-number", "filename", "--format", "json"});
        runs.add(new String[] {"extract", root, "-r", "--set", "title=X", "--merge", "overwrite"});
        runs.add(new String[] {"extract", root, "--pattern", "{titl}"}); // usage error
        runs.add(new String[] {"extract", root, "--pattern", "{title}.mp3"}); // usage error
        runs.add(new String[] {"extract", music.resolve("missing").toString(), "--set", "genre=Pop"});
        for (String[] args : runs) {
            run(args);
            assertThat(snapshot()).as("after %s", String.join(" ", args)).isEqualTo(before);
        }
        assertThat(tempDir.resolve("config")).doesNotExist();
        assertThat(tempDir.resolve("data")).doesNotExist();
    }

    @Test
    void readOnlyFilesAndFoldersArePreviewedAndStayUntouched() throws IOException {
        Set<PosixFilePermission> readOnlyFile = PosixFilePermissions.fromString("r--r--r--");
        Set<PosixFilePermission> readOnlyDir = PosixFilePermissions.fromString("r-xr-xr-x");
        List<Path> files;
        try (Stream<Path> list = Files.list(schemeDot)) {
            files = list.toList();
        }
        try {
            for (Path file : files) {
                Files.setPosixFilePermissions(file, readOnlyFile);
            }
            Files.setPosixFilePermissions(schemeDot, readOnlyDir);
            assumeTrue(!Files.isWritable(files.getFirst()), "running as root: permissions are not enforced");
            Map<String, String> before = snapshot();

            int code = run("extract", schemeDot.toString(), "--pattern", "{album} ({year})/{track}. {artist} - {title}",
                    "--format", "json");

            assertThat(code).as("stdout %s stderr %s", out, err).isEqualTo(ExitCode.OK);
            assertThat(items()).hasSize(2).allSatisfy((path, item) -> assertThat(status(item)).isEqualTo("matched"));
            assertThat(out.toString()).contains("\"unreadable\":[]");
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            Files.setPosixFilePermissions(schemeDot, PosixFilePermissions.fromString("rwxr-xr-x"));
            for (Path file : files) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
            }
        }
    }
}
