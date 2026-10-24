package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

/** Turns scan results into display strings shared by all renderers. */
final class Rows {

    /** The tag fields shown per file, in column order. */
    static final Field[] FIELDS = {Field.TITLE, Field.ARTIST, Field.ALBUM_ARTIST, Field.ALBUM, Field.TRACK,
            Field.DISC, Field.YEAR, Field.GENRE, Field.COMMENT};

    private Rows() {
    }

    /** Column title of a field, e.g. "ALBUM ARTIST". */
    static String header(Field field) {
        return field.name().replace('_', ' ');
    }

    /** Column width of a field's values in the tables (scan and extract). */
    static int width(Field field) {
        return switch (field) {
            case TITLE -> 28;
            case ARTIST, ALBUM -> 22;
            case ALBUM_ARTIST -> 18;
            case TRACK -> 5;
            case DISC, YEAR -> 4;
            case GENRE -> 14;
            case COMMENT -> 16;
        };
    }

    /** The effective (v2 first, v1 fallback) display value on one line, or "". */
    static String value(ScannedFile file, Field field) {
        return file.tags().effective().display(field).map(Text::oneLine).orElse("");
    }

    /** e.g. "ID3v1.1 + ID3v2.3"; "" for an untagged file. */
    static String versions(TagReadResult tags) {
        return tags.versions().stream().map(TagVersion::label).collect(Collectors.joining(" + "));
    }

    /** "yes" for a front cover, "other" for another picture type, "no" without picture. */
    static String cover(TagReadResult tags) {
        return tags.cover().map(CoverInfo::type).map(t -> t == PictureType.FRONT_COVER ? "yes" : "other")
                .orElse("no");
    }

    static String duration(TagReadResult tags) {
        return tags.stream().map(s -> Text.duration(s.duration())).orElse("");
    }

    static String bitrate(TagReadResult tags) {
        return tags.stream().filter(s -> s.bitrateKbps() > 0)
                .map(s -> s.bitrateKbps() + (s.variableBitrate() ? " VBR" : "")).orElse("");
    }

    /** The ID3v1 values of conflicting fields, e.g. "artist: v1 'Artist B'". */
    static String differs(ScannedFile file) {
        return file.conflicts().stream()
                .map(field -> field.placeholder() + ": v1 '" + file.tags().layer(TagFamily.ID3V1)
                        .flatMap(layer -> layer.metadata().display(field)).map(Text::oneLine).orElse("") + "'")
                .collect(Collectors.joining(", "));
    }

    /** Differing v1 values and read warnings, separated by "; ". */
    static String notes(ScannedFile file) {
        String differs = differs(file);
        String issues = file.tags().issues().stream().map(issue -> Text.oneLine(issue.message()))
                .collect(Collectors.joining("; "));
        return differs.isEmpty() ? issues : issues.isEmpty() ? differs : differs + "; " + issues;
    }

    /** The path relative to the scanned folder, with "/" as separator on every OS. */
    static String relative(Path root, Path path) {
        Path relative = path.startsWith(root) ? root.relativize(path) : path;
        return relative.toString().replace('\\', '/');
    }

    /** e.g. "hidden", "directory symlink", "excluded". */
    static String reason(SkipReason reason) {
        return reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
