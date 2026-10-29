package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyNumberTotal;
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;
import org.jaudiotagger.tag.images.Artwork;

/**
 * Maps what jaudiotagger parsed to core's format-neutral {@link TagReadResult}. Portable text fields come through the
 * generic {@code FieldKey} API; the frame level is used only where the generic API would hide information: raw
 * TRCK/TPOS text (to report invalid values), raw TCON text (genre references) and picture types.
 */
final class Id3Reading {

    private static final Pattern POSITION = Pattern.compile("^\\s*(\\d{1,9})\\s*(?:/\\s*(\\d{1,9}))?\\s*$");
    private static final Pattern YEAR = Pattern.compile("^\\s*(\\d{4})(?:\\D.*)?$", Pattern.DOTALL);
    private static final int FRONT_COVER = 3;

    private Id3Reading() {}

    /** Frame ids that differ between ID3v2.2 and 2.3/2.4. */
    private record FrameIds(String track, String disc, String genre) {
        static final FrameIds V2_2 = new FrameIds("TRK", "TPA", "TCO");
        static final FrameIds V2_3_OR_4 = new FrameIds("TRCK", "TPOS", "TCON");
    }

    static TagReadResult read(MP3File mp3, Path file) {
        List<Issue> issues = new ArrayList<>();
        List<TagLayer> layers = new ArrayList<>();
        Optional<CoverInfo> cover = Optional.empty();

        if (mp3.hasID3v2Tag()) {
            AbstractID3v2Tag v2 = mp3.getID3v2Tag();
            layers.add(new TagLayer(versionOf(v2), readV2(v2, issues)));
            cover = coverOf(v2);
        } else if (startsWithId3Header(file)) {
            issues.add(Issue.error(IssueCode.TAG_DAMAGED, "the ID3v2 tag at the start of the file is damaged and "
                    + "could not be read"));
        }
        if (mp3.hasID3v1Tag()) {
            ID3v1Tag v1 = mp3.getID3v1Tag();
            layers.add(new TagLayer(v1 instanceof ID3v11Tag ? TagVersion.ID3V1_1 : TagVersion.ID3V1_0,
                    readV1(v1, issues)));
        }
        return new TagReadResult(MediaFormat.MP3, layers, streamOf(mp3.getMP3AudioHeader()), cover, issues);
    }

    // ---- stream -------------------------------------------------------------------------------------------------

    private static Optional<StreamInfo> streamOf(MP3AudioHeader header) {
        if (header == null) {
            return Optional.empty();
        }
        long millis = Math.max(0, Math.round(header.getPreciseTrackLength() * 1000.0));
        int bitrate = (int) Math.max(0, header.getBitRateAsNumber());
        return Optional.of(new StreamInfo(Duration.ofMillis(millis), bitrate, header.isVariableBitRate()));
    }

    // ---- ID3v2 --------------------------------------------------------------------------------------------------

    private static TagVersion versionOf(AbstractID3v2Tag tag) {
        if (tag instanceof ID3v24Tag) {
            return TagVersion.ID3V2_4;
        }
        if (tag instanceof ID3v23Tag) {
            return TagVersion.ID3V2_3;
        }
        if (tag instanceof ID3v22Tag) {
            return TagVersion.ID3V2_2;
        }
        throw new IllegalStateException("unknown ID3v2 tag class " + tag.getClass().getName());
    }

    private static MediaMetadata readV2(AbstractID3v2Tag tag, List<Issue> issues) {
        FrameIds ids = tag instanceof ID3v22Tag ? FrameIds.V2_2 : FrameIds.V2_3_OR_4;
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        putText(values, Field.TITLE, joined(tag, FieldKey.TITLE), false);
        putText(values, Field.ARTIST, joined(tag, FieldKey.ARTIST), false);
        putText(values, Field.ALBUM_ARTIST, joined(tag, FieldKey.ALBUM_ARTIST), false);
        putText(values, Field.ALBUM, joined(tag, FieldKey.ALBUM), false);
        putYear(values, tag.getFirst(FieldKey.YEAR), issues);
        putPosition(values, Field.TRACK, rawText(tag, ids.track()), issues);
        putPosition(values, Field.DISC, rawText(tag, ids.disc()), issues);
        putGenres(values, rawTextValues(tag, ids.genre()));
        putText(values, Field.COMMENT, commentOf(tag), true);
        return new MediaMetadata(values);
    }

    private static String joined(AbstractID3v2Tag tag, FieldKey key) {
        List<String> parts = new ArrayList<>();
        for (String value : tag.getAll(key)) {
            String cleaned = clean(value, false);
            if (!cleaned.isEmpty()) {
                parts.add(cleaned);
            }
        }
        return String.join("; ", new LinkedHashSet<>(parts));
    }

    /** The comment frame without a description; frames with a description (iTunes data and the like) are skipped. */
    private static String commentOf(AbstractID3v2Tag tag) {
        for (TagField field : tag.getFields(FieldKey.COMMENT)) {
            if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyCOMM comment
                    && clean(comment.getDescription(), false).isEmpty()) {
                String text = clean(comment.getText(), true);
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    /** Raw text of the first frame with this id, or empty. */
    private static String rawText(AbstractID3v2Tag tag, String frameId) {
        List<String> values = rawTextValues(tag, frameId);
        return values.isEmpty() ? "" : values.get(0);
    }

    /** Raw text of the first frame with this id, split at NUL (multi-value frames); empty list if absent. */
    private static List<String> rawTextValues(AbstractID3v2Tag tag, String frameId) {
        List<TagField> frames = tag.getFrame(frameId);
        if (frames == null || frames.isEmpty() || !(frames.get(0) instanceof AbstractID3v2Frame frame)) {
            return List.of();
        }
        String raw;
        if (frame.getBody() instanceof AbstractFrameBodyNumberTotal numberTotal) {
            raw = numberTotal.getText();
        } else if (frame.getBody() instanceof AbstractFrameBodyTextInfo text) {
            raw = text.getText();
        } else {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split("\u0000")) {
                String cleaned = clean(part, false);
                if (!cleaned.isEmpty()) {
                    parts.add(cleaned);
                }
            }
        }
        return parts;
    }

    private static Optional<CoverInfo> coverOf(AbstractID3v2Tag tag) {
        List<Artwork> pictures = tag.getArtworkList();
        if (pictures == null || pictures.isEmpty()) {
            return Optional.empty();
        }
        Artwork chosen = pictures.get(0);
        for (Artwork picture : pictures) {
            if (picture.getPictureType() == FRONT_COVER) {
                chosen = picture;
                break;
            }
        }
        byte[] data = chosen.isLinked() ? null : chosen.getBinaryData();
        String mime = chosen.getMimeType();
        return Optional.of(new CoverInfo(chosen.getPictureType() == FRONT_COVER ? PictureType.FRONT_COVER : PictureType.OTHER,
                mime == null || mime.isBlank() ? "application/octet-stream" : mime.strip(),
                data == null ? 0 : data.length));
    }

    // ---- ID3v1 --------------------------------------------------------------------------------------------------

    private static MediaMetadata readV1(ID3v1Tag tag, List<Issue> issues) {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        putText(values, Field.TITLE, tag.getFirstTitle(), false);
        putText(values, Field.ARTIST, tag.getFirstArtist(), false);
        putText(values, Field.ALBUM, tag.getFirstAlbum(), false);
        putYear(values, tag.getFirstYear(), issues);
        putText(values, Field.COMMENT, tag.getFirstComment(), true);
        if (tag instanceof ID3v11Tag) {
            putPosition(values, Field.TRACK, clean(tag.getFirstTrack(), false), issues);
        }
        String genre = clean(tag.getFirstGenre(), false);
        if (!genre.isEmpty()) {
            OptionalInt number = Id3v1Genres.number(genre);
            String canonical = number.isPresent() ? Id3v1Genres.name(number.getAsInt()).orElse(genre) : genre;
            values.put(Field.GENRE, new FieldValue.Genres(List.of(new Genre(canonical))));
        }
        return new MediaMetadata(values);
    }

    // ---- value mapping ------------------------------------------------------------------------------------------

    /**
     * Strips padding, NULs and surrounding whitespace. Single-line fields get their line breaks replaced by spaces.
     */
    static String clean(String raw, boolean multiLine) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\u0000", "");
        if (!multiLine) {
            text = text.replaceAll("\\s*[\\r\\n]+\\s*", " ");
        }
        return text.strip();
    }

    private static void putText(Map<Field, FieldValue> values, Field field, String raw, boolean multiLine) {
        String text = clean(raw, multiLine);
        if (!text.isEmpty()) {
            values.put(field, new FieldValue.Text(text));
        }
    }

    private static void putYear(Map<Field, FieldValue> values, String raw, List<Issue> issues) {
        String text = clean(raw, false);
        if (text.isEmpty()) {
            return;
        }
        Matcher matcher = YEAR.matcher(text);
        if (!matcher.matches()) {
            issues.add(invalid(Field.YEAR, text));
            return;
        }
        int year = Integer.parseInt(matcher.group(1));
        if (year > 0) { // 0000 is how some taggers write "no year"
            values.put(Field.YEAR, new FieldValue.YearValue(year));
        }
    }

    private static void putPosition(Map<Field, FieldValue> values, Field field, String raw, List<Issue> issues) {
        String text = clean(raw, false);
        if (text.isEmpty()) {
            return;
        }
        Matcher matcher = POSITION.matcher(text);
        if (!matcher.matches()) {
            issues.add(invalid(field, text));
            return;
        }
        long number = Long.parseLong(matcher.group(1));
        long total = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
        if (number == 0) {
            return; // "0" means "not set" (ID3v1 track 0 is absent as well)
        }
        if (number > 9999 || total > 9999) {
            issues.add(invalid(field, text));
            return;
        }
        values.put(field, new FieldValue.Position((int) number,
                total == 0 ? OptionalInt.empty() : OptionalInt.of((int) total)));
    }

    private static void putGenres(Map<Field, FieldValue> values, List<String> rawParts) {
        Set<String> seen = new LinkedHashSet<>();
        List<Genre> genres = new ArrayList<>();
        for (String raw : rawParts) {
            for (String part : raw.split("\\s*;\\s*")) { // "A; B" is how ID3v2.2/2.3 hold several genres
                for (Genre genre : Id3v1Genres.resolveReference(part).orElse(List.of())) {
                    if (seen.add(genre.name().toLowerCase(java.util.Locale.ROOT))) {
                        genres.add(genre);
                    }
                }
            }
        }
        if (!genres.isEmpty()) {
            values.put(Field.GENRE, new FieldValue.Genres(genres));
        }
    }

    private static Issue invalid(Field field, String raw) {
        return Issue.warning(IssueCode.READ_VALUE_INVALID, field + " has an invalid value: " + raw).forField(field);
    }

    /** True if the file begins with an "ID3" header, i.e. an ID3v2 tag exists even though the library found none. */
    private static boolean startsWithId3Header(Path file) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            byte[] header = new byte[3];
            return in.read(header) == 3 && header[0] == 'I' && header[1] == 'D' && header[2] == '3';
        } catch (IOException e) {
            return false;
        }
    }
}
