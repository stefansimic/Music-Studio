package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * The tag-writing options of commands that write files (story 004's {@code export}): which ID3 versions, which text
 * encodings, and what happens to ID3v1 for Greek or Cyrillic text. A command adds them with {@code @Mixin}. The values
 * are parsed as text so the mixin needs no reflection beyond picocli's own; a wrong value or an invalid combination is a
 * usage error (exit code 2) with the message of {@link TagWriteOptions}.
 */
public final class Id3OptionsMixin {

    @Spec(Spec.Target.MIXEE)
    private CommandSpec spec;

    @Option(names = "--id3v2", defaultValue = "2.3", paramLabel = "<version>",
            description = "ID3v2 version to write: none, 2.2, 2.3 or 2.4 (default: ${DEFAULT-VALUE}).")
    private String id3v2;

    @Option(names = "--id3v1", defaultValue = "1.1", paramLabel = "<version>",
            description = "ID3v1 version to write: none, 1.0 or 1.1 (default: ${DEFAULT-VALUE}).")
    private String id3v1;

    @Option(names = "--v2-encoding", defaultValue = "auto", paramLabel = "<encoding>",
            description = "Text encoding of ID3v2 frames: auto, latin1, utf16 or utf8 (utf8 only with 2.4; "
                    + "default: ${DEFAULT-VALUE}).")
    private String v2Encoding;

    @Option(names = "--v1-mode", defaultValue = "ascii", paramLabel = "<mode>",
            description = "Latin text in ID3v1: ascii (ü becomes ue) or latin1 (default: ${DEFAULT-VALUE}).")
    private String v1Mode;

    @Option(names = "--v1-non-latin", defaultValue = "skip", paramLabel = "<policy>",
            description = "ID3v1 for Greek, Cyrillic and other non-Latin text: skip (no ID3v1 tag for that file; "
                    + "ID3v2 keeps the text), codepage (Windows-1253 or -1251 bytes) or transliterate "
                    + "(default: ${DEFAULT-VALUE}).")
    private String v1NonLatin;

    @Option(names = "--remove-other-versions",
            description = "Remove the existing tag of a tag family set to none instead of keeping it.")
    private boolean removeOtherVersions;

    /**
     * @return the options the user selected; never null
     * @throws ParameterException (usage error) for an unknown value or a combination TagWriteOptions rejects
     */
    public TagWriteOptions toOptions() {
        Id3v2Choice v2 = choice("--id3v2", id3v2, List.of("none", "2.2", "2.3", "2.4"),
                List.of(Id3v2Choice.NONE, Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4));
        Id3v1Choice v1 = choice("--id3v1", id3v1, List.of("none", "1.0", "1.1"),
                List.of(Id3v1Choice.NONE, Id3v1Choice.V1_0, Id3v1Choice.V1_1));
        V2Encoding encoding = choice("--v2-encoding", v2Encoding, List.of("auto", "latin1", "utf16", "utf8"),
                List.of(V2Encoding.AUTO, V2Encoding.LATIN1, V2Encoding.UTF16, V2Encoding.UTF8));
        V1TextMode mode = choice("--v1-mode", v1Mode, List.of("ascii", "latin1"),
                List.of(V1TextMode.ASCII, V1TextMode.LATIN1));
        V1NonLatinPolicy policy = choice("--v1-non-latin", v1NonLatin, List.of("skip", "codepage", "transliterate"),
                List.of(V1NonLatinPolicy.SKIP_V1, V1NonLatinPolicy.CODEPAGE, V1NonLatinPolicy.TRANSLITERATE));
        try {
            return new TagWriteOptions(v2, v1, encoding, mode, policy, removeOtherVersions);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    private <T> T choice(String option, String value, List<String> names, List<T> values) {
        int index = names.indexOf(value.toLowerCase(Locale.ROOT));
        if (index < 0) {
            throw new ParameterException(spec.commandLine(),
                    "Invalid value '" + value + "' for " + option + ": use one of " + String.join(", ", names));
        }
        return values.get(index);
    }
}
