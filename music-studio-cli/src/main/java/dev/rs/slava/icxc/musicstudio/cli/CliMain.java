package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Entry point of the {@code musicstudio} CLI. */
public final class CliMain {

    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private CliMain() {
    }

    /**
     * Wires the application, runs the command line and exits with its code. Ctrl-C cancels the running use case
     * and waits for it to return (so a temp file in progress is cleaned up).
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromEnvironment(System.getenv(), System.getProperties());
        } catch (IllegalArgumentException e) {
            System.err.println("musicstudio: cannot determine the application folders: " + e.getMessage());
            System.exit(ExitCode.USAGE);
            return;
        }
        MusicStudio studio = new MusicStudioFactory(config).create();
        CancellationSource cancel = new CancellationSource();
        CountDownLatch finished = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cancel.cancel();
            try {
                finished.await(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "musicstudio-cancel"));
        int code = MusicStudioCommand.commandLine(studio, cancel, Optional.of(config.dataDir().resolve("reports")))
                .execute(args);
        finished.countDown();
        System.exit(code);
    }
}
