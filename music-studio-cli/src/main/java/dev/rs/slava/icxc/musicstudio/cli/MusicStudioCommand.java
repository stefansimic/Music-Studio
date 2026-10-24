package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The top-level {@code musicstudio} command. Without a subcommand it prints the usage. */
@Command(name = "musicstudio", mixinStandardHelpOptions = true, version = "musicstudio 0.1.0",
        description = "Organizes a music library for playback on other devices.")
public final class MusicStudioCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    /**
     * Builds the command line with all subcommands.
     *
     * @param studio the wired use cases; not null
     * @param cancel cancelled by Ctrl-C; not null
     * @return the configured command line; never null
     */
    public static CommandLine commandLine(MusicStudio studio, Cancellation cancel) {
        Require.nonNull(studio, "studio");
        Require.nonNull(cancel, "cancel");
        CommandLine commandLine = new CommandLine(new MusicStudioCommand());
        commandLine.addSubcommand(new CommandLine(new ScanCommand(studio.scan(), cancel)));
        commandLine.addSubcommand(new CommandLine(new ExtractCommand(studio.scan(), studio.propose(), cancel)));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return ExitCode.USAGE;
    }
}
