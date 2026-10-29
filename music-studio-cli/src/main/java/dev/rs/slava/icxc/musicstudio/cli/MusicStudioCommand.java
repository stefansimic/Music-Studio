package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The top-level {@code musicstudio} command: prints its help when called without a subcommand. */
@Command(name = "musicstudio", mixinStandardHelpOptions = true, version = MusicStudioCommand.VERSION,
        description = "Organises a music library: reads tags, genres and cover art.")
public final class MusicStudioCommand implements Runnable {

    /** Shown by {@code --version}. */
    static final String VERSION = "musicstudio 0.1.0-SNAPSHOT";

    @Spec
    private CommandSpec spec;

    /**
     * Builds the complete command line: this command plus all subcommands wired to the application's use cases.
     *
     * @param studio the wired use cases; not null
     * @param cancel cancellation shared by all commands, triggered by Ctrl-C; not null
     * @param out    standard output; not null
     * @param err    standard error; not null
     * @return a ready-to-run command line; unknown format values are rejected case-insensitively with exit code 2
     */
    public static CommandLine create(MusicStudio studio, Cancellation cancel, PrintWriter out, PrintWriter err) {
        Require.nonNull(studio, "studio");
        Require.nonNull(cancel, "cancel");
        Require.nonNull(out, "out");
        Require.nonNull(err, "err");
        CommandLine commandLine = new CommandLine(new MusicStudioCommand());
        commandLine.addSubcommand("scan", new ScanCommand(studio.scan(), cancel));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.getCommandSpec().exitCodeOnInvalidInput(ExitCode.USAGE);
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> {
            cmd.getErr().println("error: " + exception);
            cmd.getErr().flush();
            return ExitCode.ABORTED;
        });
        return commandLine;
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
