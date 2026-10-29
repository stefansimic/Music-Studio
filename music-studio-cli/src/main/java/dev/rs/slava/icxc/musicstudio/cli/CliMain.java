package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import java.io.BufferedWriter;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Entry point of the {@code musicstudio} command-line tool. */
public final class CliMain {

    private CliMain() {}

    /** @param args the command-line arguments */
    public static void main(String[] args) {
        MusicStudio studio = new MusicStudioFactory(
                AppConfig.fromEnvironment(System.getenv(), System.getProperties())).create();
        CancellationSource cancel = new CancellationSource();
        CountDownLatch finished = new CountDownLatch(1);
        // Ctrl-C: ask the running command to stop, then give it a moment to finish cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cancel.cancel();
            try {
                finished.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        PrintWriter out = writer(FileDescriptor.out);
        PrintWriter err = writer(FileDescriptor.err);
        int exitCode = MusicStudioCommand.create(studio, cancel, out, err).execute(args);
        out.flush();
        err.flush();
        finished.countDown();
        System.exit(exitCode);
    }

    /** UTF-8 regardless of the locale, so tags in any script come out intact. */
    private static PrintWriter writer(FileDescriptor descriptor) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(descriptor),
                StandardCharsets.UTF_8)), false);
    }
}
