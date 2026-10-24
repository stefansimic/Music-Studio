package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The matching semantics as a table (architecture §10.2, ADR 0010). Expected values are given as
 * {@code field=raw&field=raw}; every surprising real-world name should become a new row.
 */
class PathMatcherTest {

    private static final Path ROOT = Path.of("/m").toAbsolutePath();

    static MatchResult match(String pattern, String relative) {
        return PathMatcher.match(PatternParserTest.valid(pattern), ROOT.resolve(relative));
    }

    private static Map<Field, String> raw(String spec) {
        java.util.Map<Field, String> map = new java.util.EnumMap<>(Field.class);
        if (spec == null || spec.isBlank()) {
            return map;
        }
        for (String pair : spec.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(Field.byPlaceholder(kv[0]).orElseThrow(), kv[1]);
        }
        return map;
    }

    @ParameterizedTest(name = "{0} | {1} -> {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            // pattern | relative path | kind | expected raw (chosen) | expected alternative
            "{album}/{artist} - {title} - {year}|Best Of 2020/Artist - Song - 2020.mp3|MATCHED|album=Best Of 2020&artist=Artist&title=Song&year=2020|",
            "{album}/{artist}_{title}|Album/Artist_Song.mp3|MATCHED|album=Album&artist=Artist&title=Song|",
            "{artist} - {title}|A - B - C.mp3|AMBIGUOUS|artist=A&title=B - C|artist=A - B&title=C",
            "{artist} - {title}|Jay-Z - Song.mp3|MATCHED|artist=Jay-Z&title=Song|",
            "{artist} - {title}|Artist-Song.mp3|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|Artist   -    Song .mp3|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|Artist - - Song.mp3|AMBIGUOUS|artist=Artist&title=- Song|artist=Artist -&title=Song",
            "{artist} - {title} - {year}|A - B - C - 2020.mp3|AMBIGUOUS|artist=A&title=B - C&year=2020|artist=A - B&title=C",
            "{title} - {year}|Song - Live - 2020.mp3|MATCHED|title=Song - Live&year=2020|",
            "{artist} - {title} {year}|A - B (2020).mp3|MATCHED|artist=A&title=B&year=(2020)|",
            "{artist} - {title} {year}|A - B [2020].mp3|MATCHED|artist=A&title=B&year=[2020]|",
            "{title} ({year})|Song (2020).mp3|MATCHED|title=Song&year=2020|",
            "{track} - {artist} - {title}|01 - A - B.mp3|MATCHED|track=01&artist=A&title=B|",
            "{track}. {title}|01.Song.mp3|MATCHED|track=01&title=Song|",
            "{track}. {title}|01. Song.mp3|MATCHED|track=01&title=Song|",
            "{track}_{title}|01_Song.mp3|MATCHED|track=01&title=Song|",
            "{track} {title}|3of12 Song.mp3|MATCHED|track=3of12&title=Song|",
            "{track} {title}|03 Song.mp3|MATCHED|track=03&title=Song|",
            "{artist} - {title}|01 - A - B.mp3|AMBIGUOUS|artist=01&title=A - B|artist=01 - A&title=B",
            "{*} - {artist} - {title}|01 - A - B.mp3|MATCHED|artist=A&title=B|",
            "{year} - {album}/{track} - {title}|2020 - Album/01 - Song.mp3|MATCHED|year=2020&album=Album&track=01&title=Song|",
            "{artist}/{album}/{title}|X/Y/Z.mp3|MATCHED|artist=X&album=Y&title=Z|",
            "{artist} - {title}|Ђорђе Балашевић - Неки Нови Клинци.mp3|MATCHED|artist=Ђорђе Балашевић&title=Неки Нови Клинци|",
            "{title}|Song 🎵.mp3|MATCHED|title=Song 🎵|",
            "{artist} - {title}|A - B.mp3|MATCHED|artist=A&title=B|",
            "{title}|My.Song.mp3|MATCHED|title=My.Song|",
            "{title}|noext|MATCHED|title=noext|",
            "{genre} - {title}|Pop; Rock - Song.mp3|MATCHED|genre=Pop; Rock&title=Song|",
            "{year}{track} {title}|202003 Song.mp3|MATCHED|year=2020&track=03&title=Song|",
            "{track}{title}|07Song.mp3|MATCHED|track=07&title=Song|",
    })
    void table(String pattern, String path, MatchKind kind, String expected, String alternative) {
        MatchResult result = match(pattern, path);
        assertThat(result.kind()).as("kind; result %s", result).isEqualTo(kind);
        assertThat(result.raw()).as("raw").isEqualTo(raw(expected));
        assertThat(result.alternative()).as("alternative").isEqualTo(raw(alternative));
        assertThat(result.values().keySet()).isEqualTo(result.raw().keySet());
        if (kind == MatchKind.AMBIGUOUS) {
            assertThat(result.alternative()).isNotEmpty().isNotEqualTo(result.raw());
        }
    }

    @ParameterizedTest(name = "{0} | {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "{artist} - {title}|Artist Song.mp3|separator \" - \" not found after \"Artist Song\"",
            "{artist} - {title} - {year}|A - B - 20x0.mp3|20x0",
            "{artist}/{album}/{title}|Z.mp3|levels",
            "{title} - {year}|Song - 2020 (Remix).mp3|unexpected text \"(Remix)\" at the end",
            "{artist} feat. {title}|A Feat. B.mp3|feat.",
            "{title}|.mp3|empty",
            "{artist} - {title}|'A - B\nC.mp3'|control character",
            "{album}/{artist} - {title}|Best Of 2020/NoSeparator.mp3|file name \"NoSeparator\"",
            "{year} - {album}/{title}|Album/Song.mp3|folder \"Album\"",
            "{artist} - {title}|Artist - .mp3|ends before {title}",
    })
    void noMatchWithReason(String pattern, String path, String reasonPart) {
        MatchResult result = match(pattern, path);
        assertThat(result.kind()).as("result %s", result).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.values()).isEmpty();
        assertThat(result.reason()).hasValueSatisfying(reason -> assertThat(reason).contains(reasonPart));
    }

    @Test
    void typedValues() {
        MatchResult result = match("{album}/{track} - {artist} - {title} - {year}",
                "Best Of/3of12 - Artist - Song - (2020).mp3");
        assertThat(result.values()).containsEntry(Field.TRACK, Position.of(3, 12))
                .containsEntry(Field.YEAR, new YearValue(2020))
                .containsEntry(Field.ARTIST, new Text("Artist"))
                .containsEntry(Field.ALBUM, new Text("Best Of"));
    }

    @Test
    void nfdNamesYieldNfcValues() {
        String nfd = Normalizer.normalize("Đorđe Šćepan - Pesma", Normalizer.Form.NFD);
        MatchResult result = match("{artist} - {title}", nfd + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.ARTIST, new Text("Đorđe Šćepan"));
    }

    @Test
    void wildcardNeverAppearsInTheResult() {
        assertThat(match("{*} - {title}", "x - Song.mp3").raw()).containsOnlyKeys(Field.TITLE);
    }

    @Test
    void ambiguityInTheFolderAloneIsAmbiguous() {
        MatchResult result = match("{artist} - {album}/{title}", "A - B - C/Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.alternative()).containsOnlyKeys(Field.ARTIST, Field.ALBUM);
    }

    @Test
    void noMatchInAnySegmentWins() {
        MatchResult result = match("{artist} - {album}/{track} - {title}", "A - B - C/Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason()).hasValueSatisfying(r -> assertThat(r).startsWith("file name \"Song\""));
    }

    @Test
    void ancestorsAboveTheScanRootAreMatched() {
        assertThat(PathMatcher.match(PatternParserTest.valid("{album}/{title}"), Path.of("/Album/Song.mp3")
                .toAbsolutePath()).raw()).containsEntry(Field.ALBUM, "Album");
    }

    @Test
    void preconditions() {
        InputPattern pattern = PatternParserTest.valid("{title}");
        assertThatThrownBy(() -> PathMatcher.match(null, ROOT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PathMatcher.match(pattern, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PathMatcher.match(pattern, ROOT.getRoot()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchResultInvariants() {
        assertThatThrownBy(() -> new MatchResult(MatchKind.AMBIGUOUS, Map.of(), Map.of(), Map.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResult(MatchKind.MATCHED, Map.of(Field.TITLE, new Text("x")), Map.of(),
                Map.of(), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchResult.noMatch(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
