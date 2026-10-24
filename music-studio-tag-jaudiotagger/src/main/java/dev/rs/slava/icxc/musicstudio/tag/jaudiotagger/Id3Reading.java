package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.datatype.DataTypes;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyNumberTotal;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;
import org.jaudiotagger.tag.id3.framebody.FrameBodyDeprecated;
import org.jaudiotagger.tag.id3.framebody.FrameBodyUnsupported;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.reference.GenreTypes;

/**
 * Maps jaudiotagger's ID3 objects to core's format-neutral model. Generic {@link FieldKey}s are used for portable
 * text fields; the frame level only for the raw TRCK/TPOS/TCON text (so invalid values and genre references are
 * interpreted by core rules) and for pictures.
 */
final class Id3Reading {

    private static final Pattern POSITION = Pattern.compile("^(\\d{1,9})\\s*(?:/\\s*(\\d{0,9}))?$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})(?:\\D.*)?$");
    private static final int APIC_FRONT_COVER = 3;
    private static final String UNKNOWN_MIME = "application/octet-stream";

    private Id3Reading() {
    }

    // --- ID3v2 ----------------------------------------------------------------------------------------------

    static TagLayer v2Layer(AbstractID3v2Tag tag, List<Issue> issues) {
        TagVersion version = switch (tag) {
            case ID3v22Tag ignored -> TagVersion.ID3V2_2;
            case ID3v24Tag ignored -> TagVersion.ID3V2_4;
            default -> TagVersion.ID3V2_3;
        };
        boolean v22 = version == TagVersion.ID3V2_2;
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        text(tag.getAll(FieldKey.TITLE)).ifPresent(v -> values.put(Field.TITLE, v));
        text(tag.getAll(FieldKey.ARTIST)).ifPresent(v -> values.put(Field.ARTIST, v));
        text(tag.getAll(FieldKey.ALBUM_ARTIST)).ifPresent(v -> values.put(Field.ALBUM_ARTIST, v));
        text(tag.getAll(FieldKey.ALBUM)).ifPresent(v -> values.put(Field.ALBUM, v));
        userComment(tag, v22 ? "COM" : "COMM").ifPresent(v -> values.put(Field.COMMENT, v));
        year(yearText(tag, version), issues).ifPresent(v -> values.put(Field.YEAR, v));
        position(Field.TRACK, numberTotalText(tag, v22 ? "TRK" : "TRCK"), issues)
                .ifPresent(v -> values.put(Field.TRACK, v));
        position(Field.DISC, numberTotalText(tag, v22 ? "TPA" : "TPOS"), issues)
                .ifPresent(v -> values.put(Field.DISC, v));
        genres(genreValues(tag, v22 ? "TCO" : "TCON")).ifPresent(v -> values.put(Field.GENRE, v));
        if (tag.getInvalidFrames() > 0) {
            issues.add(damaged(tag.getInvalidFrames() + " damaged ID3v2 frame(s) could not be read and were ignored"));
        }
        return new TagLayer(version, new MediaMetadata(values));
    }

    /** An ERROR issue: part of a tag could not be read; the readable rest is still returned. */
    static Issue damaged(String message) {
        return Issue.error(IssueCode.TAG_DAMAGED, message);
    }

    /**
     * The user's comment is the COMM frame with an empty description. Others (iTunNORM, iTunSMPB, …) are technical
     * data written by players, not comments.
     */
    private static Optional<FieldValue> userComment(AbstractID3v2Tag tag, String frameId) {
        for (TagField field : tag.getFields(frameId)) {
            if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyCOMM body
                    && clean(body.getDescription()).isEmpty()) {
                Optional<FieldValue> text = multiLineText(body.getText());
                if (text.isPresent()) {
                    return text;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The year text from the version's native frame, falling back to the other version's frame: taggers that mix
     * versions write TDRC into v2.3 and TYER into v2.4 tags, and jaudiotagger's generic YEAR key ignores those.
     */
    private static String yearText(AbstractID3v2Tag tag, TagVersion version) {
        List<String> frameIds = switch (version) {
            case ID3V2_2 -> List.of("TYE");
            case ID3V2_4 -> List.of("TDRC", "TYER");
            default -> List.of("TYER", "TDRC");
        };
        for (String frameId : frameIds) {
            for (TagField field : tag.getFields(frameId)) {
                if (field instanceof AbstractID3v2Frame frame) {
                    Object frameBody = frame.getBody() instanceof FrameBodyDeprecated deprecated
                            ? deprecated.getOriginalFrameBody() // e.g. TYER inside a v2.4 tag
                            : frame.getBody();
                    String text = switch (frameBody) {
                        case AbstractFrameBodyTextInfo body -> body.getText();
                        case FrameBodyUnsupported body -> decodeTextFrame(body.getObjectValue(DataTypes.OBJ_DATA));
                        default -> "";
                    };
                    if (!clean(text).isEmpty()) {
                        return text;
                    }
                }
            }
        }
        // TYER together with TDAT/TIME is merged into an aggregated field on reading; the generic key covers it
        return tag.getFirst(FieldKey.YEAR);
    }

    /** Decodes the raw body of a text frame that jaudiotagger does not know in this version. */
    private static String decodeTextFrame(Object data) {
        if (!(data instanceof byte[] bytes) || bytes.length < 2) {
            return "";
        }
        Charset charset = switch (bytes[0]) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        return new String(bytes, 1, bytes.length - 1, charset);
    }

    private static Optional<String> numberTotalText(AbstractID3v2Tag tag, String frameId) {
        for (TagField field : tag.getFields(frameId)) {
            if (field instanceof AbstractID3v2Frame frame
                    && frame.getBody() instanceof AbstractFrameBodyNumberTotal body) {
                return Optional.ofNullable(body.getText());
            }
        }
        return Optional.empty();
    }

    private static List<String> genreValues(AbstractID3v2Tag tag, String frameId) {
        List<String> values = new ArrayList<>();
        for (TagField field : tag.getFields(frameId)) {
            if (field instanceof AbstractID3v2Frame frame
                    && frame.getBody() instanceof AbstractFrameBodyTextInfo body) {
                values.addAll(body.getValues());
            }
        }
        return values;
    }

    static Optional<Artwork> coverArtwork(AbstractID3v2Tag tag) {
        List<Artwork> artworks = tag.getArtworkList();
        for (Artwork artwork : artworks) {
            if (artwork.getPictureType() == APIC_FRONT_COVER && hasData(artwork)) {
                return Optional.of(artwork);
            }
        }
        return artworks.stream().filter(Id3Reading::hasData).findFirst();
    }

    static CoverInfo coverInfo(Artwork artwork) {
        return new CoverInfo(pictureType(artwork), mime(artwork), artwork.getBinaryData().length);
    }

    static CoverArt coverArt(Artwork artwork) {
        return new CoverArt(artwork.getBinaryData(), mime(artwork), pictureType(artwork));
    }

    private static boolean hasData(Artwork artwork) {
        return artwork.getBinaryData() != null && artwork.getBinaryData().length > 0;
    }

    private static PictureType pictureType(Artwork artwork) {
        return artwork.getPictureType() == APIC_FRONT_COVER ? PictureType.FRONT_COVER : PictureType.OTHER;
    }

    private static String mime(Artwork artwork) {
        String mime = clean(artwork.getMimeType());
        return mime.isEmpty() ? UNKNOWN_MIME : mime.toLowerCase(Locale.ROOT);
    }

    // --- ID3v1 ----------------------------------------------------------------------------------------------

    static TagLayer v1Layer(ID3v1Tag tag, List<Issue> issues) {
        boolean v11 = tag instanceof ID3v11Tag;
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        text(List.of(tag.getFirst(FieldKey.TITLE))).ifPresent(v -> values.put(Field.TITLE, v));
        text(List.of(tag.getFirst(FieldKey.ARTIST))).ifPresent(v -> values.put(Field.ARTIST, v));
        text(List.of(tag.getFirst(FieldKey.ALBUM))).ifPresent(v -> values.put(Field.ALBUM, v));
        multiLineText(tag.getFirst(FieldKey.COMMENT)).ifPresent(v -> values.put(Field.COMMENT, v));
        String year = clean(tag.getFirst(FieldKey.YEAR));
        if (!year.isEmpty() && !year.chars().allMatch(c -> c == '0')) {
            year(year, issues).ifPresent(v -> values.put(Field.YEAR, v));
        }
        if (v11) {
            String track = clean(tag.getFirst(FieldKey.TRACK));
            if (!track.isEmpty() && !track.equals("0")) {
                position(Field.TRACK, Optional.of(track), issues).ifPresent(v -> values.put(Field.TRACK, v));
            }
        }
        v1Genre(clean(tag.getFirst(FieldKey.GENRE))).ifPresent(v -> values.put(Field.GENRE, v));
        return new TagLayer(v11 ? TagVersion.ID3V1_1 : TagVersion.ID3V1_0, new MediaMetadata(values));
    }

    /** jaudiotagger returns the genre name from its own table; map it back to the byte and use core's table. */
    private static Optional<FieldValue> v1Genre(String name) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Integer id = GenreTypes.getInstanceOf().getIdForValue(name);
        Optional<String> ours = id == null ? Optional.empty() : Id3v1Genres.name(id);
        return ours.<FieldValue>map(Genres::of).or(() -> genres(List.of(name)));
    }

    // --- stream ---------------------------------------------------------------------------------------------

    static Optional<StreamInfo> stream(MP3AudioHeader header) {
        if (header == null) {
            return Optional.empty();
        }
        double seconds = Math.max(0, header.getPreciseTrackLength());
        Duration duration = Duration.ofNanos(Math.round(seconds * 1_000_000_000d));
        int bitrate = (int) Math.max(0, Math.min(Integer.MAX_VALUE, header.getBitRateAsNumber()));
        return Optional.of(new StreamInfo(duration, bitrate, header.isVariableBitRate()));
    }

    // --- values ---------------------------------------------------------------------------------------------

    /** Removes NUL padding and surrounding whitespace. */
    static String clean(String raw) {
        return raw == null ? "" : raw.replace("\0", "").strip();
    }

    /** Several values (v2.4 multi-value frames) are joined with "; ". Line breaks become spaces. */
    private static Optional<FieldValue> text(List<String> raw) {
        String joined = raw.stream()
                .map(Id3Reading::clean)
                .map(v -> v.replaceAll("\\R+", " ").strip())
                .filter(v -> !v.isEmpty())
                .distinct()
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? Optional.empty() : Optional.of(new Text(joined));
    }

    private static Optional<FieldValue> multiLineText(String raw) {
        String value = clean(raw);
        return value.isEmpty() ? Optional.empty() : Optional.of(new Text(value));
    }

    private static Optional<FieldValue> year(String raw, List<Issue> issues) {
        String value = clean(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = YEAR.matcher(value);
        int year = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
        if (year < 1) {
            issues.add(invalid(Field.YEAR, value));
            return Optional.empty();
        }
        return Optional.of(new YearValue(year));
    }

    private static Optional<FieldValue> position(Field field, Optional<String> raw, List<Issue> issues) {
        String value = clean(raw.orElse(""));
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = POSITION.matcher(value);
        if (!matcher.matches()) {
            issues.add(invalid(field, value));
            return Optional.empty();
        }
        long number = Long.parseLong(matcher.group(1));
        String totalText = matcher.group(2);
        long total = totalText == null || totalText.isEmpty() ? 0 : Long.parseLong(totalText);
        if (number < 1 || number > 9999 || total > 9999) {
            issues.add(invalid(field, value));
            return Optional.empty();
        }
        return Optional.of(new Position((int) number, total == 0 ? OptionalInt.empty() : OptionalInt.of((int) total)));
    }

    private static Optional<FieldValue> genres(List<String> raw) {
        List<Genre> genres = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : raw) {
            Id3v1Genres.resolveReference(clean(value)).ifPresent(resolved -> {
                for (Genre genre : resolved) {
                    if (seen.add(genre.name().toLowerCase(Locale.ROOT))) {
                        genres.add(genre);
                    }
                }
            });
        }
        return genres.isEmpty() ? Optional.empty() : Optional.of(new Genres(genres));
    }

    private static Issue invalid(Field field, String raw) {
        return Issue.warning(IssueCode.READ_VALUE_INVALID,
                field.name() + ": '" + raw + "' is not a valid " + field.placeholder() + " value; ignored")
                .forField(field);
    }
}
