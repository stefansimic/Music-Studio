package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import picocli.CommandLine.Option;

/** The tag write options shared by commands that write: ID3 versions, encodings, other-version handling. */
public final class WriteOptions {

    @Option(names = "--id3v2", paramLabel = "none|2.2|2.3|2.4", defaultValue = "2.3",
            description = "ID3v2 version to write (default: VALUE; 2.3 is understood by almost every "
                    + "device).")
    private String id3v2;

    @Option(names = "--id3v1", paramLabel = "none|1.0|1.1", defaultValue = "1.1",
            description = "ID3v1 version to write (default: VALUE; for old car stereos and players).")
    private String id3v1;

    @Option(names = "--v2-encoding", paramLabel = "auto|latin1|utf16|utf8", defaultValue = "auto",
            description = "Text encoding of the ID3v2 tag (default: VALUE). utf8 needs --id3v2 2.4.")
    private String v2Encoding;

    @Option(names = "--v1-text", paramLabel = "ascii|latin1", defaultValue = "ascii",
            description = "Characters allowed in ID3v1 (default: VALUE; others are transliterated).")
    private String v1Text;

    @Option(names = "--remove-other-versions",
            description = "Remove a tag whose version is set to none instead of keeping it unchanged.")
    private boolean removeOtherVersions;

    /**
     * Converts the flags to core options.
     *
     * @param err receives one line per problem; not null
     * @return the options, or empty if a value is unknown or the combination is impossible
     */
    public Optional<TagWriteOptions> toOptions(PrintWriter err) {
        Require.nonNull(err, "err");
        List<String> errors = new ArrayList<>();
        Id3v2Choice v2 = switch (id3v2.toLowerCase(Locale.ROOT)) {
            case "none" -> Id3v2Choice.NONE;
            case "2.2" -> Id3v2Choice.V2_2;
            case "2.3" -> Id3v2Choice.V2_3;
            case "2.4" -> Id3v2Choice.V2_4;
            default -> bad(errors, "--id3v2 " + id3v2 + ": expected none, 2.2, 2.3 or 2.4", Id3v2Choice.V2_3);
        };
        Id3v1Choice v1 = switch (id3v1.toLowerCase(Locale.ROOT)) {
            case "none" -> Id3v1Choice.NONE;
            case "1.0" -> Id3v1Choice.V1_0;
            case "1.1" -> Id3v1Choice.V1_1;
            default -> bad(errors, "--id3v1 " + id3v1 + ": expected none, 1.0 or 1.1", Id3v1Choice.V1_1);
        };
        V2Encoding encoding = switch (v2Encoding.toLowerCase(Locale.ROOT)) {
            case "auto" -> V2Encoding.AUTO;
            case "latin1" -> V2Encoding.LATIN1;
            case "utf16" -> V2Encoding.UTF16;
            case "utf8" -> V2Encoding.UTF8;
            default -> bad(errors, "--v2-encoding " + v2Encoding + ": expected auto, latin1, utf16 or utf8",
                    V2Encoding.AUTO);
        };
        V1TextMode mode = switch (v1Text.toLowerCase(Locale.ROOT)) {
            case "ascii" -> V1TextMode.ASCII;
            case "latin1" -> V1TextMode.LATIN1;
            default -> bad(errors, "--v1-text " + v1Text + ": expected ascii or latin1", V1TextMode.ASCII);
        };
        if (errors.isEmpty()) {
            errors.addAll(TagWriteOptions.problems(v2, v1, encoding, mode, removeOtherVersions));
        }
        if (!errors.isEmpty()) {
            errors.forEach(message -> err.println("musicstudio: " + message));
            return Optional.empty();
        }
        return Optional.of(new TagWriteOptions(v2, v1, encoding, mode, removeOtherVersions));
    }

    private static <T> T bad(List<String> errors, String message, T fallback) {
        errors.add(message);
        return fallback;
    }
}
