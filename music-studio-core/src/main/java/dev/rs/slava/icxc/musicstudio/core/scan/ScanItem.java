package dev.rs.slava.icxc.musicstudio.core.scan;

import java.nio.file.Path;

/** One result of a scan: a file that was read, or a file that could not be read. */
public sealed interface ScanItem permits ScannedFile, UnreadableFile {

    /** @return the absolute path of the file; never null */
    Path path();
}
