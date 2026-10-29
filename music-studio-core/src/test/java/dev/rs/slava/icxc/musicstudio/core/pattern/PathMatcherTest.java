package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** M01-M16 and A04/A05: the matcher on literals, spaces and typed placeholders. */
class PathMatcherTest {

    static final Path ROOT = Path.of("/music");

    static MatchResult match(String pattern, String relativePath) {
        return PathMatcher.match(PatternParserTest.in(pattern), ROOT.resolve(relativePath));
    }

    static FieldValue.Text text(String value) {
        return new FieldValue.Text(value);
    }

    // ---- M01 / M02 ----------------------------------------------------------------------------------------------

    @Test
    void basicSplit() {
        MatchResult result = match("{artist} - {title} - {year}", "Artist - Song - 2020.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.alternativeCount()).isZero();
        assertThat(result.reason()).isEmpty();
        assertThat(result.values()).containsEntry(Field.ARTIST, text("Artist")).containsEntry(Field.TITLE, text("Song"))
                .containsEntry(Field.YEAR, new FieldValue.YearValue(2020)).hasSize(3);
        assertThat(result.raw()).containsEntry(Field.YEAR, "2020").containsEntry(Field.ARTIST, "Artist");
    }

    @Test
    void storyPatternWithFolder() {
        MatchResult result = match("{album}/{artist} - {title} - {year}", "Best Of 2020/Artist - Song - 2020.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.ALBUM, text("Best Of 2020"))
                .containsEntry(Field.YEAR, new FieldValue.YearValue(2020));
    }

    @Test
    void underscoreSeparator() {
        MatchResult result = match("{album}/{artist}_{title}", "Album/Artist_Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.ALBUM, text("Album")).containsEntry(Field.ARTIST, text("Artist"))
                .containsEntry(Field.TITLE, text("Song"));
    }

    // ---- M03 ----------------------------------------------------------------------------------------------------

    @Test
    void literalsAreCaseSensitive() {
        assertThat(match("{artist} Feat. {title}", "A Feat. B.mp3").kind()).isEqualTo(MatchKind.MATCHED);
        MatchResult result = match("{artist} Feat. {title}", "A feat. B.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason().orElseThrow()).contains("Feat.");
        assertThat(result.values()).isEmpty();
    }

    // ---- M04 ----------------------------------------------------------------------------------------------------

    @Test
    void extraSpacesAreTolerated() {
        for (String name : List.of("Artist  -   Song.mp3", " Artist - Song .mp3", "Artist-Song.mp3", "Artist -Song.mp3",
                "Artist\t-\tSong.mp3")) {
            MatchResult result = match("{artist} - {title}", name);
            assertThat(result.kind()).as(name).isEqualTo(MatchKind.MATCHED);
            assertThat(result.values()).as(name).containsEntry(Field.ARTIST, text("Artist"))
                    .containsEntry(Field.TITLE, text("Song"));
            assertThat(result.raw().get(Field.ARTIST)).as(name).isEqualTo("Artist");
        }
    }

    // ---- M05 ----------------------------------------------------------------------------------------------------

    @Test
    void missingSeparatorInSomeFilesGivesNoMatchNamingIt() {
        List<String> names = List.of("A - One.mp3", "Artist Song.mp3", "B - Two.mp3");
        List<MatchResult> results = names.stream().map(n -> match("{artist} - {title}", n)).toList();
        assertThat(results).extracting(MatchResult::kind)
                .containsExactly(MatchKind.MATCHED, MatchKind.NO_MATCH, MatchKind.MATCHED);
        assertThat(results.get(1).reason().orElseThrow()).contains("\" - \"").contains("Artist Song");
    }

    @Test
    void noMatchReasonSeparatorWording() {
        assertThat(match("{artist} - {title}", "Artist Song.mp3").reason())
                .contains("separator \" - \" not found after \"Artist Song\"");
    }

    // ---- M06 ----------------------------------------------------------------------------------------------------

    @Test
    void doubleSeparatorIsSplitAtTheFirstOneAndReportedAmbiguous() {
        MatchResult result = match("{artist} - {title}", "Artist - - Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.TITLE, "- Song");
    }

    // ---- M07 ----------------------------------------------------------------------------------------------------

    @Test
    void nonGreedyLeftToRightIsAmbiguous() {
        MatchResult result = match("{artist} - {title}", "A - B - C.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.alternativeCount()).isEqualTo(1);
        assertThat(result.values()).containsEntry(Field.ARTIST, text("A")).containsEntry(Field.TITLE, text("B - C"));
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B - C");
    }

    @Test
    void countIsCappedAtTheFirstAlternative() {
        MatchResult result = match("{artist} - {title}", "A - B - C - D - E - F.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.alternativeCount()).isEqualTo(1);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A");
    }

    // ---- M08 / M09 / M10 ----------------------------------------------------------------------------------------

    @Test
    void typedPlaceholderResolvesAmbiguity() {
        MatchResult result = match("{title} - {year}", "Artist - Song - 2020.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.alternativeCount()).isZero();
        assertThat(result.values()).containsEntry(Field.TITLE, text("Artist - Song"))
                .containsEntry(Field.YEAR, new FieldValue.YearValue(2020));
    }

    @Test
    void typedTrackResolvesTheSplit() {
        MatchResult ok = match("{track} - {title}", "01 - Artist - Song.mp3");
        assertThat(ok.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(ok.values()).containsEntry(Field.TRACK, new FieldValue.Position(1, OptionalInt.empty()))
                .containsEntry(Field.TITLE, text("Artist - Song"));
        MatchResult bad = match("{track} - {title}", "Artist - 01 - Song.mp3");
        assertThat(bad.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(bad.reason().orElseThrow()).contains("\"Artist\" is not a track number");
    }

    @Test
    void invalidTypedValueIsNamedInTheReason() {
        MatchResult result = match("{artist} - {year}", "Artist - 20x0.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason().orElseThrow()).contains("\"20x0\" is not a 4-digit year");
    }

    @Test
    void reasonCarriesTheSegmentIndexForFolderPatterns() {
        MatchResult result = match("{album}/{artist} - {year}", "Album/Artist - 20x0.mp3");
        assertThat(result.reason().orElseThrow()).contains("part 2 of 2").contains("\"20x0\" is not a 4-digit year");
    }

    // ---- M11 ----------------------------------------------------------------------------------------------------

    @Test
    void wildcardAloneMatchesAnythingAndExtractsNothing() {
        for (String name : List.of("anything.mp3", "A - B.mp3", "Ђорђе.mp3")) {
            MatchResult result = match("{*}", name);
            assertThat(result.kind()).as(name).isEqualTo(MatchKind.MATCHED);
            assertThat(result.values()).as(name).isEmpty();
            assertThat(result.raw()).as(name).isEmpty();
        }
    }

    @Test
    void wildcardMayBeEmpty() {
        MatchResult result = match("{*} - {title}", "- Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsOnlyKeys(Field.TITLE).containsEntry(Field.TITLE, text("Song"));
    }

    @Test
    void twoWildcardsAreFine() {
        MatchResult result = match("{*} - {*} - {title}", "a - b - Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsOnlyKeys(Field.TITLE).containsEntry(Field.TITLE, text("Song"));
        assertThat(match("{*}/{*}/{title}", "x/y/Song.mp3").values()).containsOnlyKeys(Field.TITLE);
    }

    @Test
    void wildcardPlusPlaceholdersLikeTheStory() {
        MatchResult result = match("{*} - {artist} - {title}", "Junk - A - B.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsOnlyKeys(Field.ARTIST, Field.TITLE);
    }

    // ---- M12 ----------------------------------------------------------------------------------------------------

    @Test
    void extensionHandling() {
        assertThat(match("{title}", "Song.mp3").raw()).containsEntry(Field.TITLE, "Song");
        assertThat(match("{title}", "Song.v2.mp3").raw()).containsEntry(Field.TITLE, "Song.v2");
        assertThat(match("{title}", "Song").raw()).containsEntry(Field.TITLE, "Song");
        assertThat(match("{title}", ".hidden.mp3").raw()).containsEntry(Field.TITLE, ".hidden");
        assertThat(match("{title}", "Song.MP3").raw()).containsEntry(Field.TITLE, "Song");
        assertThat(match("{title}", "Song.Mp3").raw()).containsEntry(Field.TITLE, "Song");
    }

    @Test
    void theExtensionIsOnlyRemovedFromTheFileNameNotFromFolders() {
        assertThat(match("{album}/{title}", "Album.mp3/Song.mp3").raw()).containsEntry(Field.ALBUM, "Album.mp3");
    }

    // ---- M13 / A05 ----------------------------------------------------------------------------------------------

    @Test
    void wrongFolderRightFileNameNamesTheFolderSegment() {
        MatchResult result = match("{year} - {album}/{artist} - {title}", "Album/Artist - Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason().orElseThrow()).contains("part 1 of 2").contains("Album");
    }

    @Test
    void folderSegmentsMatchIndependently() {
        MatchResult result = match("{artist}/{album}/{track} - {title}", "Artist/Album/03 - Song.mp3");
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.ALBUM, "Album")
                .containsEntry(Field.TRACK, "03").containsEntry(Field.TITLE, "Song");
    }

    @Test
    void ambiguityInAFolderSegmentIsReported() {
        MatchResult result = match("{artist} - {album}/{title}", "A - B - C/Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.ALBUM, "B - C");
    }

    @Test
    void pathWithFewerElementsThanSegmentsIsNoMatch() {
        for (Path path : List.of(Path.of("/a.mp3"), Path.of("/x/a.mp3"))) {
            MatchResult result = PathMatcher.match(PatternParserTest.in("{artist}/{album}/{title}"), path);
            assertThat(result.kind()).as(path.toString()).isEqualTo(MatchKind.NO_MATCH);
            assertThat(result.reason().orElseThrow()).contains("3");
        }
        MatchResult relative = PathMatcher.match(PatternParserTest.in("{artist}/{album}/{title}"), Path.of("x/a.mp3"));
        assertThat(relative.kind()).isEqualTo(MatchKind.NO_MATCH);
    }

    @Test
    void scanRootNameCanBeUsedAsAlbumOrArtist() {
        MatchResult result = match("{artist}/{album}/{title}", "Alb/Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "music").containsEntry(Field.ALBUM, "Alb");
    }

    // ---- M15 ----------------------------------------------------------------------------------------------------

    @Test
    void pathologicalInputIsFast() {
        String name = "a - ".repeat(75) + "z";
        assertThat(name.length()).isGreaterThanOrEqualTo(300);
        Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
            MatchResult result = match("{title} - {artist} - {albumartist} - {album} - {comment} - {genre}", name + ".mp3");
            assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
            assertThat(result.alternativeCount()).isEqualTo(1);
        });
        Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
            MatchResult result = match("{*} - {*} - {*} - {*} - {title}", "- ".repeat(200) + "x.mp3");
            assertThat(result.kind()).isIn(MatchKind.AMBIGUOUS, MatchKind.MATCHED);
        });
        Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
            MatchResult result = match("{title} - {artist} - {year}", "x".repeat(300) + " - y.mp3");
            assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        });
    }

    // ---- M16 ----------------------------------------------------------------------------------------------------

    @Test
    void sameInputSameResultAThousandTimes() {
        InputPattern pattern = PatternParserTest.in("{artist} - {title}");
        Path path = ROOT.resolve("A - B - C.mp3");
        MatchResult first = PathMatcher.match(pattern, path);
        for (int i = 0; i < 1_000; i++) {
            assertThat(PathMatcher.match(pattern, path)).isEqualTo(first);
        }
    }

    @Test
    void parallelRunsGiveTheSameResults() throws Exception {
        InputPattern pattern = PatternParserTest.in("{album}/{artist} - {title}");
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            paths.add(ROOT.resolve("Album " + i + "/Artist " + i + " - Song " + (i % 7 == 0 ? "- x " : "") + i + ".mp3"));
        }
        List<MatchResult> expected = paths.stream().map(p -> PathMatcher.match(pattern, p)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<MatchResult>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                Callable<List<MatchResult>> job = () -> paths.stream().map(p -> PathMatcher.match(pattern, p)).toList();
                futures.add(pool.submit(job));
            }
            for (Future<List<MatchResult>> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- result invariants / preconditions ----------------------------------------------------------------------

    @Test
    void resultMapsAreUnmodifiable() {
        MatchResult result = match("{artist} - {title}", "A - B.mp3");
        assertThatThrownBy(() -> result.values().put(Field.YEAR, new FieldValue.YearValue(2000)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.raw().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void matchResultInvariants() {
        assertThatThrownBy(() -> new MatchResult(MatchKind.NO_MATCH, java.util.Map.of(), java.util.Map.of(), 0,
                java.util.Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResult(MatchKind.MATCHED, java.util.Map.of(), java.util.Map.of(), 1,
                java.util.Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResult(MatchKind.MATCHED, java.util.Map.of(), java.util.Map.of(), 0,
                java.util.Optional.of("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResult(MatchKind.MATCHED, java.util.Map.of(Field.TITLE, text("x")),
                java.util.Map.of(), 0, java.util.Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchResult.noMatch(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullArguments() {
        assertThatThrownBy(() -> PathMatcher.match(null, ROOT.resolve("a.mp3"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PathMatcher.match(PatternParserTest.in("{title}"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void genresAreTypedAsGenres() {
        MatchResult result = match("{genre} - {title}", "Pop; Rock - Song.mp3");
        assertThat(result.values().get(Field.GENRE))
                .isEqualTo(new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock"))));
    }
}
