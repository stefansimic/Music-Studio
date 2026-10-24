package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Composes the target file: new head bytes, the source payload copied byte for byte, new tail bytes. The source is
 * opened read-only; the target is created new (never replaced) and forced to disk before it is closed.
 */
final class PayloadCopy {

    private static final int BUFFER = 1024 * 1024;

    private PayloadCopy() {
    }

    static void compose(Path source, long from, long to, Path target, byte[] head, byte[] tail, Cancellation cancel,
                        ByteProgress progress) throws IOException {
        long total = head.length + (to - from) + tail.length;
        try (FileChannel in = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeFully(out, ByteBuffer.wrap(head));
            long done = head.length;
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
            long position = from;
            while (position < to) {
                cancel.throwIfCancelled();
                buffer.clear();
                buffer.limit((int) Math.min(BUFFER, to - position));
                int read = in.read(buffer, position);
                if (read < 0) {
                    throw new IOException("the source became shorter while it was copied");
                }
                buffer.flip();
                writeFully(out, buffer);
                position += read;
                done += read;
                progress.onBytes(done, total);
            }
            writeFully(out, ByteBuffer.wrap(tail));
            out.force(true);
            progress.onBytes(total, total);
        }
    }

    private static void writeFully(FileChannel out, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }
}
