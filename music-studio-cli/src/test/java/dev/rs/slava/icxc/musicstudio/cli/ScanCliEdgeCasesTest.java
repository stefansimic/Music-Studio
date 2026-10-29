package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonSyntax;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Command-line behaviour around the edges: argument shapes, exit codes, streams, odd file names and file systems. */
class ScanCliEdgeCasesTest {

    @TempDir
    Path tempDir;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void reset() {
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    private int run(String... args) {
        MusicStudio studio = new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"),
                Clock.systemUTC())).create();
        PrintWriter o = new PrintWriter(out);
        PrintWriter e = new PrintWriter(err);
        int code = MusicStudioCommand.create(studio, new CancellationSource(), o, e).execute(args);
        o.flush();
        e.flush();
        return code;
    }

    private Path library(String dirName) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve(dirName));
        Fixtures.copyTo(Fixture.V23_LATIN1, dir);
        Fixtures.copyTo(Fixture.NO_TAGS, dir);
        return dir;
    }

    @Test
    void folderWithSpacesUnicodeAndTrailingSlash() throws Exception {
        Path dir = library("Моја музика čćž 🎵 with spaces");
        assertThat(run("scan", dir + "/")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("v23-latin1.mp3").contains("no-tags.mp3").contains("2 files found");
    }

    @Test
    void relativeFolderArgumentWorks() throws Exception {
        Path dir = library("rel");
        Path relative = Path.of("").toAbsolutePath().relativize(dir);
        assertThat(relative.isAbsolute()).isFalse();
        assertThat(run("scan", relative.toString())).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("2 files found");
    }

    @Test
    void dotDotSegmentsAreNormalised() throws Exception {
        Path dir = library("dots");
        assertThat(run("scan", dir.resolve("..").resolve("dots").toString())).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("2 files found").doesNotContain("..");
    }

    @Test
    void formatIsCaseInsensitiveAndOptionOrderDoesNotMatter() throws Exception {
        Path dir = library("order");
        assertThat(run("scan", "--format", "JSON", dir.toString(), "-r")).isEqualTo(ExitCode.OK);
        JsonSyntax.assertValid(out.toString());
        reset();
        assertThat(run("scan", "-f", "Csv", "--recursive", dir.toString())).isEqualTo(ExitCode.OK);
        assertThat(out.toString().lines().count()).isEqualTo(3);
        reset();
        assertThat(run("scan", "--format=json", dir.toString())).isEqualTo(ExitCode.OK);
        JsonSyntax.assertValid(out.toString());
    }

    @Test
    void jsonAndCsvKeepStdoutMachineReadableAndSendDiagnosticsToStderr() throws Exception {
        Path dir = library("streams");
        Files.writeString(dir.resolve("bad.mp3"), "garbage garbage");
        Files.createDirectories(dir.resolve(".hid"));
        assertThat(run("scan", dir.toString(), "-f", "json")).isEqualTo(ExitCode.ITEM_ERRORS);
        JsonSyntax.assertValid(out.toString());
        assertThat(out.toString().strip()).startsWith("{").endsWith("}");
        reset();
        assertThat(run("scan", dir.toString(), "-f", "csv")).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(out.toString().lines().count()).isEqualTo(4); // header + 3 mp3
        assertThat(out.toString()).doesNotContain("completed:");
        assertThat(err.toString()).contains("completed:");
    }

    @Test
    void helpAndVersionNeverTouchTheFileSystem() {
        assertThat(run("scan", "-h")).isEqualTo(ExitCode.OK);
        assertThat(run("-V")).isEqualTo(ExitCode.OK);
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void invalidArgumentsAreExitCodeTwoAndGoToStderr() throws Exception {
        Path dir = library("bad-args");
        assertThat(run("scan", dir.toString(), "--format", "xml")).isEqualTo(ExitCode.USAGE);
        assertThat(run("scan")).isEqualTo(ExitCode.USAGE);
        assertThat(run("scan", dir.toString(), dir.toString())).isEqualTo(ExitCode.USAGE);
        assertThat(run("frobnicate")).isEqualTo(ExitCode.USAGE);
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).isNotBlank();
    }

    @Test
    void filenamesWithCommasQuotesAndNewlinesSurviveCsvAndJson() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("odd"));
        Fixtures.copyTo(Fixture.V23_LATIN1, dir, "a, \"quoted\" b.mp3");
        Fixtures.copyTo(Fixture.V23_LATIN1, dir, "back\\slash.mp3");
        Fixtures.copyTo(Fixture.V23_LATIN1, dir, "emoji 🎵 中文.mp3");
        run("scan", dir.toString(), "-f", "csv");
        assertThat(out.toString()).contains("a, \"\"quoted\"\" b.mp3\"").contains("emoji 🎵 中文.mp3");
        reset();
        run("scan", dir.toString(), "-f", "json");
        JsonSyntax.assertValid(out.toString());
        assertThat(out.toString()).contains("back\\\\slash.mp3").contains("\\\"quoted\\\"");
    }

    @Test
    void illegalOnFat32FileNamesAreScanned() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("fat"));
        for (String name : new String[] {"what?.mp3", "a:b.mp3", "x<y>.mp3", "pipe|star*.mp3"}) {
            Fixtures.copyTo(Fixture.V23_LATIN1, dir, name);
        }
        assertThat(run("scan", dir.toString())).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("4 files found").contains("what?.mp3").contains("a:b.mp3");
    }

    @Test
    void unreadableFileIsListedWithAnErrorAndTheScanContinues() throws Exception {
        assumeTrue(!"root".equals(System.getProperty("user.name")));
        Path dir = library("perm");
        Path locked = Fixtures.copyTo(Fixture.V23_LATIN1, dir, "locked.mp3");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assumeTrue(!Files.isReadable(locked));
            assertThat(run("scan", dir.toString())).isEqualTo(ExitCode.ITEM_ERRORS);
            assertThat(out.toString()).contains("locked.mp3").contains("ERROR").contains("no-tags.mp3")
                    .contains("3 files found").contains("1 unreadable");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void unreadableSubfolderDoesNotStopTheRecursiveScan() throws Exception {
        assumeTrue(!"root".equals(System.getProperty("user.name")));
        Path dir = library("permdir");
        Path sub = Files.createDirectories(dir.resolve("locked-dir"));
        Fixtures.copyTo(Fixture.V23_LATIN1, sub, "inside.mp3");
        Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("---------"));
        try {
            assumeTrue(!Files.isReadable(sub));
            int code = run("scan", dir.toString(), "-r");
            assertThat(out.toString()).contains("v23-latin1.mp3").contains("no-tags.mp3");
            assertThat(code).isIn(ExitCode.OK, ExitCode.ITEM_ERRORS);
            assertThat(out.toString() + err.toString()).containsIgnoringCase("locked-dir");
        } finally {
            Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void symlinksAreHandledWithoutLoopingOrLeavingTheRoot() throws Exception {
        Path dir = library("links");
        Path outside = library("outside");
        Files.createSymbolicLink(dir.resolve("file-link.mp3"), outside.resolve("v23-latin1.mp3"));
        Files.createSymbolicLink(dir.resolve("dir-link"), outside);
        Files.createSymbolicLink(dir.resolve("loop"), dir);
        Files.createSymbolicLink(dir.resolve("dangling.mp3"), tempDir.resolve("does-not-exist.mp3"));
        int code = run("scan", dir.toString(), "-r");
        String table = out.toString();
        assertThat(table).contains("file-link.mp3");
        // the directory links are reported as skipped and not followed: nothing from "outside" is listed under them
        assertThat(table).doesNotContain("dir-link/").doesNotContain("loop/");
        assertThat(table).contains("dir-link").contains("loop");
        assertThat(code).isIn(ExitCode.OK, ExitCode.ITEM_ERRORS);
    }

    @Test
    void scanningNeverChangesFilesOrModificationTimes() throws Exception {
        Path dir = library("ro");
        Files.writeString(dir.resolve("bad.mp3"), "x");
        java.util.Map<String, String> before = snapshot(dir);
        run("scan", dir.toString(), "-r", "--include-hidden", "--strict-v1-compare");
        run("scan", dir.toString(), "-f", "json");
        run("scan", dir.toString(), "-f", "csv");
        assertThat(snapshot(dir)).isEqualTo(before);
    }

    private static java.util.Map<String, String> snapshot(Path dir) throws IOException {
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.toList()) {
                result.put(dir.relativize(p).toString(), (Files.isRegularFile(p) ? Fixtures.sha256(p) : "dir") + " "
                        + Files.getLastModifiedTime(p).toMillis());
            }
        }
        return result;
    }
}
