package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Hand-written expectation for every fixture. The values are copied from {@code generate_fixtures.py}; they were
 * never produced by running our own reader.
 */
public final class FixtureCatalog {

    /** Size of the 1x1 JPEG embedded in the cover fixtures (output of the generator script). */
    public static final int COVER_SIZE_BYTES = 225;
    /** Every readable fixture is 1 s of 128 kbps CBR audio; readers may deviate by {@link #DURATION_TOLERANCE}. */
    public static final StreamInfo NOMINAL_STREAM = new StreamInfo(Duration.ofSeconds(1), 128, false);
    public static final Duration DURATION_TOLERANCE = Duration.ofMillis(100);

    /** What a reader must do with a fixture. */
    public sealed interface Expectation {
        /** The fixture is readable and yields this result (stream info nominal, see {@link #NOMINAL_STREAM}). */
        record Readable(TagReadResult result) implements Expectation {}

        /** The fixture cannot be read and fails with this reason. */
        record Unreadable(MediaAccessException.Reason reason) implements Expectation {}
    }

    private static final Map<Fixture, Expectation> EXPECTATIONS = new EnumMap<>(Fixture.class);
    private static final Map<String, Fixture> BY_SHA = new HashMap<>();

    static {
        EXPECTATIONS.put(Fixture.NO_TAGS, readable());
        EXPECTATIONS.put(Fixture.V1_0, readable(layer(TagVersion.ID3V1_0, meta(
                Field.TITLE, text("Title v1.0"), Field.ARTIST, text("Artist v1"), Field.ALBUM, text("Album v1"),
                Field.YEAR, year(1999), Field.COMMENT, text("Comment of exactly 30 bytes!!!")))));
        EXPECTATIONS.put(Fixture.V1_1, readable(layer(TagVersion.ID3V1_1, meta(
                Field.TITLE, text("Title v1.1"), Field.ARTIST, text("Artist v1.1"), Field.ALBUM, text("Album v1.1"),
                Field.YEAR, year(2001), Field.COMMENT, text("Comment v1.1"), Field.TRACK, position(7, 0),
                Field.GENRE, genres("Rock")))));
        EXPECTATIONS.put(Fixture.V23_LATIN1, readable(layer(TagVersion.ID3V2_3, meta(
                Field.TITLE, text("Café au lait"), Field.ARTIST, text("Artist Latin1"),
                Field.ALBUM_ARTIST, text("Album Artist"), Field.ALBUM, text("Album Latin1"), Field.YEAR, year(2005),
                Field.TRACK, position(3, 12), Field.DISC, position(1, 2), Field.GENRE, genres("Jazz"),
                Field.COMMENT, text("A comment")))));
        EXPECTATIONS.put(Fixture.V23_UTF16_CYRILLIC, readable(layer(TagVersion.ID3V2_3, meta(
                Field.TITLE, text("čćšžđ"), Field.ARTIST, text("Ђорђе Балашевић"),
                Field.ALBUM, text("Музика 🎵"), Field.YEAR, year(2012)))));
        EXPECTATIONS.put(Fixture.V24_UTF8, readable(layer(TagVersion.ID3V2_4, meta(
                Field.TITLE, text("Title UTF-8 ñ"), Field.ARTIST, text("Artist v24"), Field.ALBUM, text("Album v24"),
                Field.YEAR, year(2020), Field.TRACK, position(5, 0), Field.GENRE, genres("Rock", "Pop")))));
        EXPECTATIONS.put(Fixture.V22, readable(layer(TagVersion.ID3V2_2, meta(
                Field.TITLE, text("Title v22"), Field.ARTIST, text("Artist v22"), Field.ALBUM, text("Album v22"),
                Field.YEAR, year(1995), Field.TRACK, position(4, 9), Field.GENRE, genres("Blues")))));
        EXPECTATIONS.put(Fixture.V11_V23_SAME, readable(
                layer(TagVersion.ID3V2_3, meta(
                        Field.TITLE, text("A title that is definitely longer than thirty characters"),
                        Field.ARTIST, text("Ђорђе Балашевић"), Field.ALBUM, text("Album Šumadija"),
                        Field.YEAR, year(2010), Field.TRACK, position(2, 10), Field.GENRE, genres("Rock"))),
                layer(TagVersion.ID3V1_1, meta(
                        Field.TITLE, text("A title that is definitely"), Field.ARTIST, text("Djordje Balasevic"),
                        Field.ALBUM, text("Album Sumadija"), Field.YEAR, year(2010), Field.TRACK, position(2, 0),
                        Field.GENRE, genres("Rock")))));
        EXPECTATIONS.put(Fixture.V11_V23_DIFF, readable(
                layer(TagVersion.ID3V2_3, meta(
                        Field.TITLE, text("Same Title"), Field.ARTIST, text("Artist Two"), Field.YEAR, year(2015),
                        Field.TRACK, position(1, 10))),
                layer(TagVersion.ID3V1_1, meta(
                        Field.TITLE, text("Same Title"), Field.ARTIST, text("Artist One"), Field.YEAR, year(2015),
                        Field.TRACK, position(1, 0)))));
        EXPECTATIONS.put(Fixture.V23_GENRE_REF, readable(layer(TagVersion.ID3V2_3, meta(
                Field.TITLE, text("Genre Ref"), Field.GENRE, genres("Rock")))));
        EXPECTATIONS.put(Fixture.V23_GENRE_NUM, readable(layer(TagVersion.ID3V2_3, meta(
                Field.TITLE, text("Genre Num"), Field.GENRE, genres("Rock")))));
        EXPECTATIONS.put(Fixture.V23_BAD_TRACK, new Expectation.Readable(result(
                List.of(layer(TagVersion.ID3V2_3, meta(Field.TITLE, text("Bad Track")))), Optional.empty(),
                List.of(Issue.warning(IssueCode.READ_VALUE_INVALID, "TRACK has an invalid value: A1")
                        .forField(Field.TRACK)))));
        EXPECTATIONS.put(Fixture.V23_COVER, new Expectation.Readable(result(
                List.of(layer(TagVersion.ID3V2_3, meta(Field.TITLE, text("With Cover")))),
                Optional.of(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", COVER_SIZE_BYTES)), List.of())));
        EXPECTATIONS.put(Fixture.V23_OTHER_PICTURE, new Expectation.Readable(result(
                List.of(layer(TagVersion.ID3V2_3, meta(Field.TITLE, text("Other Picture")))),
                Optional.of(new CoverInfo(PictureType.OTHER, "image/jpeg", COVER_SIZE_BYTES)), List.of())));
        EXPECTATIONS.put(Fixture.CORRUPT, new Expectation.Unreadable(MediaAccessException.Reason.CORRUPT));
        EXPECTATIONS.put(Fixture.EMPTY, new Expectation.Unreadable(MediaAccessException.Reason.CORRUPT));
        for (Fixture fixture : Fixture.values()) {
            BY_SHA.put(Fixtures.sha256(Fixtures.bytes(fixture)), fixture);
        }
        assert EXPECTATIONS.keySet().containsAll(List.of(Fixture.values())) : "every fixture needs an expectation";
    }

    private FixtureCatalog() {}

    /** @return the expectation for {@code fixture}; never null */
    public static Expectation expected(Fixture fixture) {
        return EXPECTATIONS.get(fixture);
    }

    /** @return the readable result for {@code fixture}; fails for CORRUPT and EMPTY */
    public static TagReadResult readable(Fixture fixture) {
        if (EXPECTATIONS.get(fixture) instanceof Expectation.Readable readable) {
            return readable.result();
        }
        throw new IllegalArgumentException(fixture + " is not readable");
    }

    /** @return the fixture with exactly this content (SHA-256 as lower-case hex), if any */
    public static Optional<Fixture> bySha256(String sha256) {
        return Optional.ofNullable(BY_SHA.get(sha256));
    }

    /** @return all fixtures that can be read */
    public static List<Fixture> readableFixtures() {
        List<Fixture> result = new ArrayList<>();
        for (Fixture fixture : Fixture.values()) {
            if (EXPECTATIONS.get(fixture) instanceof Expectation.Readable) {
                result.add(fixture);
            }
        }
        return result;
    }

    private static Expectation readable(TagLayer... layers) {
        return new Expectation.Readable(result(List.of(layers), Optional.empty(), List.of()));
    }

    private static TagReadResult result(List<TagLayer> layers, Optional<CoverInfo> cover, List<Issue> issues) {
        return new TagReadResult(MediaFormat.MP3, layers, Optional.of(NOMINAL_STREAM), cover, issues);
    }

    private static TagLayer layer(TagVersion version, MediaMetadata metadata) {
        return new TagLayer(version, metadata);
    }

    private static MediaMetadata meta(Object... fieldsAndValues) {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            values.put((Field) fieldsAndValues[i], (FieldValue) fieldsAndValues[i + 1]);
        }
        return new MediaMetadata(values);
    }

    private static FieldValue text(String value) {
        return new FieldValue.Text(value);
    }

    private static FieldValue year(int year) {
        return new FieldValue.YearValue(year);
    }

    /** @param total 0 = no total */
    private static FieldValue position(int number, int total) {
        return new FieldValue.Position(number, total == 0 ? OptionalInt.empty() : OptionalInt.of(total));
    }

    private static FieldValue genres(String... names) {
        return new FieldValue.Genres(java.util.Arrays.stream(names).map(Genre::new).toList());
    }
}
