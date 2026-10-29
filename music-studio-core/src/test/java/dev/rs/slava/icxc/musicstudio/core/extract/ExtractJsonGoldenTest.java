package dev.rs.slava.icxc.musicstudio.core.extract;

import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.genres;
import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.pattern;
import static dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataServiceTest.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.file;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.pattern.MatchResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PathMatcher;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** X03: golden JSON of the extract and pattern records (Cyrillic, emoji, sealed types with a type tag, enum map keys). */
class ExtractJsonGoldenTest {

    private static final String PATTERN = "{\"source\":\"{album}/{artist} – {title}\",\"segments\":["
            + "[{\"type\":\"PLACEHOLDER\",\"field\":\"ALBUM\",\"padWidth\":null}],"
            + "[{\"type\":\"PLACEHOLDER\",\"field\":\"ARTIST\",\"padWidth\":null},{\"type\":\"LITERAL\",\"text\":\" \u2013 \"},"
            + "{\"type\":\"PLACEHOLDER\",\"field\":\"TITLE\",\"padWidth\":null}]]}";

    private static final ScannedFile FILE = file("Теразије/Ђорђе – Song 🎵.mp3", meta(Field.COMMENT, "old \"c\"\nline"));
    private static final String FILE_JSON = "{\"path\":\"/music/Теразије/Ђорђе – Song 🎵.mp3\",\"format\":\"MP3\","
            + "\"sizeBytes\":1234,\"lastModified\":\"2021-02-03T04:05:06Z\",\"tags\":{\"COMMENT\":{\"text\":"
            + "\"old \\\"c\\\"\\nline\"}}}";

    private static ExtractionSpec fullSpec() {
        Map<Field, FieldOverride> overrides = new EnumMap<>(Field.class);
        overrides.put(Field.GENRE, new FieldOverride.Fixed(genres("Балкан", "Pop")));
        overrides.put(Field.ARTIST, new FieldOverride.KeepExisting());
        return new ExtractionSpec(Optional.of(pattern("{album}/{artist} \u2013 {title}")), overrides, MergeMode.FILL_EMPTY,
                Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK, true)), true);
    }

    private static final String SPEC_JSON = "{\"pattern\":" + PATTERN + ",\"overrides\":{\"ARTIST\":{\"type\":"
            + "\"KEEP_EXISTING\"},\"GENRE\":{\"type\":\"FIXED\",\"value\":{\"genres\":[\"Балкан\",\"Pop\"]}}},"
            + "\"mergeMode\":\"FILL_EMPTY\",\"autoNumbering\":{\"order\":\"EXISTING_TRACK\",\"setTotal\":true},"
            + "\"acceptAmbiguous\":true}";

    @Test
    void specWithSealedOverridesAndEnumKeys() {
        assertThat(ExtractJson.spec(fullSpec())).isEqualTo(SPEC_JSON);
    }

    @Test
    void emptySpecHasNullsForAbsentOptionals() {
        ExtractionSpec empty = new ExtractionSpec(Optional.empty(), Map.of(), MergeMode.OVERWRITE, Optional.empty(), false);
        assertThat(ExtractJson.spec(empty)).isEqualTo("{\"pattern\":null,\"overrides\":{},\"mergeMode\":\"OVERWRITE\","
                + "\"autoNumbering\":null,\"acceptAmbiguous\":false}");
    }

    @Test
    void requestWithEdits() {
        ManualEdits edits = new ManualEdits(Map.of(FILE.path(), Map.of(Field.TITLE, new FieldEdit.Set(text("Нова 🎶")))),
                Set.of(FILE.path()));
        ProposeRequest request = new ProposeRequest(List.of(FILE), fullSpec(), edits);
        assertThat(ExtractJson.request(request)).isEqualTo("{\"files\":[" + FILE_JSON + "],\"spec\":" + SPEC_JSON
                + ",\"edits\":{\"edits\":{\"/music/Теразије/Ђорђе – Song 🎵.mp3\":{\"TITLE\":{\"type\":\"SET\","
                + "\"value\":{\"text\":\"Нова 🎶\"}}}},\"acceptedAmbiguous\":[\"/music/Теразије/Ђорђе – Song 🎵.mp3\"]}}");
    }

    @Test
    void clearEditAndEmptyEdits() {
        ManualEdits edits = new ManualEdits(Map.of(FILE.path(), Map.of(Field.COMMENT, new FieldEdit.Clear())), Set.of());
        assertThat(ExtractJson.edits(edits)).isEqualTo("{\"edits\":{\"/music/Теразије/Ђорђе – Song 🎵.mp3\":"
                + "{\"COMMENT\":{\"type\":\"CLEAR\"}}},\"acceptedAmbiguous\":[]}");
        assertThat(ExtractJson.edits(ManualEdits.NONE)).isEqualTo("{\"edits\":{},\"acceptedAmbiguous\":[]}");
    }

    @Test
    void proposalAndProposalSet() {
        ExtractionSpec spec = ExtractionSpec.of(pattern("{album}/{artist} \u2013 {title}"));
        ProposalSet set = new ProposeMetadataService().propose(new ProposeRequest(List.of(FILE), spec, ManualEdits.NONE));
        String proposal = "{\"file\":" + FILE_JSON + ",\"status\":\"MATCHED\",\"matchDetail\":null,\"extracted\":"
                + "{\"TITLE\":\"Song 🎵\",\"ARTIST\":\"Ђорђе\",\"ALBUM\":\"Теразије\"},\"proposed\":{\"TITLE\":"
                + "{\"text\":\"Song 🎵\"},\"ARTIST\":{\"text\":\"Ђорђе\"},\"ALBUM\":{\"text\":\"Теразије\"},"
                + "\"COMMENT\":{\"text\":\"old \\\"c\\\"\\nline\"}},\"changes\":["
                + "{\"field\":\"TITLE\",\"before\":null,\"after\":{\"text\":\"Song 🎵\"},\"origin\":\"PATTERN\"},"
                + "{\"field\":\"ARTIST\",\"before\":null,\"after\":{\"text\":\"Ђорђе\"},\"origin\":\"PATTERN\"},"
                + "{\"field\":\"ALBUM\",\"before\":null,\"after\":{\"text\":\"Теразије\"},\"origin\":\"PATTERN\"}],"
                + "\"issues\":[]}";
        assertThat(ExtractJson.proposal(set.proposals().get(0))).isEqualTo(proposal);
        assertThat(ExtractJson.proposalSet(set)).isEqualTo("{\"proposals\":[" + proposal
                + "],\"matched\":1,\"ambiguous\":0,\"noMatch\":0}");
    }

    @Test
    void noMatchProposalCarriesReasonAndWarning() {
        ScannedFile bad = file("Artist Song.mp3");
        ProposalSet set = new ProposeMetadataService().propose(
                new ProposeRequest(List.of(bad), ExtractionSpec.of(pattern("{artist} - {title}")), ManualEdits.NONE));
        assertThat(ExtractJson.proposal(set.proposals().get(0))).isEqualTo("{\"file\":{\"path\":\"/music/Artist Song.mp3\","
                + "\"format\":\"MP3\",\"sizeBytes\":1234,\"lastModified\":\"2021-02-03T04:05:06Z\",\"tags\":{}},"
                + "\"status\":\"NO_MATCH\",\"matchDetail\":\"separator \\\" - \\\" not found after \\\"Artist Song\\\"\","
                + "\"extracted\":{},\"proposed\":{},\"changes\":[],\"issues\":[{\"severity\":\"WARNING\",\"code\":"
                + "\"PATTERN_NO_MATCH\",\"message\":\"separator \\\" - \\\" not found after \\\"Artist Song\\\"\","
                + "\"file\":\"/music/Artist Song.mp3\",\"field\":null}]}");
    }

    @Test
    void matchResults() {
        MatchResult matched = PathMatcher.match(pattern("{track} - {title}"), Path.of("/music/01 - Ђорђе 🎵.mp3"));
        assertThat(ExtractJson.match(matched)).isEqualTo("{\"kind\":\"MATCHED\",\"values\":{\"TITLE\":{\"text\":\"Ђорђе 🎵\"},"
                + "\"TRACK\":{\"number\":1,\"total\":null}},\"raw\":{\"TITLE\":\"Ђорђе 🎵\",\"TRACK\":\"01\"},"
                + "\"alternativeCount\":0,\"reason\":null}");
        MatchResult ambiguous = PathMatcher.match(pattern("{artist} - {title}"), Path.of("/music/A - B - C.mp3"));
        assertThat(ExtractJson.match(ambiguous)).isEqualTo("{\"kind\":\"AMBIGUOUS\",\"values\":{\"TITLE\":{\"text\":"
                + "\"B - C\"},\"ARTIST\":{\"text\":\"A\"}},\"raw\":{\"TITLE\":\"B - C\",\"ARTIST\":\"A\"},"
                + "\"alternativeCount\":1,\"reason\":null}");
        MatchResult none = PathMatcher.match(pattern("{artist} - {title}"), Path.of("/music/x.mp3"));
        assertThat(ExtractJson.match(none)).isEqualTo("{\"kind\":\"NO_MATCH\",\"values\":{},\"raw\":{},"
                + "\"alternativeCount\":0,\"reason\":" + ExtractJson.q(none.reason().orElseThrow()) + "}");
    }

    @Test
    void invalidPatternResult() {
        PatternParseResult.Invalid<?> invalid = (PatternParseResult.Invalid<?>) PatternParser.parseInput("🎵 {foo} {title");
        assertThat(ExtractJson.invalid(invalid)).startsWith("{\"errors\":[{\"column\":2,\"message\":\"Unknown placeholder"
                + " '{foo}'\"},{\"column\":8,\"message\":\"Unbalanced '{'");
    }

    @Test
    void positionWithTotalAndYear() {
        assertThat(ExtractJson.value(new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position(3, OptionalInt.of(12))))
                .isEqualTo("{\"number\":3,\"total\":12}");
        assertThat(ExtractJson.value(new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue(2020)))
                .isEqualTo("{\"year\":2020}");
    }
}
