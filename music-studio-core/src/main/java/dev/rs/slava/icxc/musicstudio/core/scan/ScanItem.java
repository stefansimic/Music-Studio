package dev.rs.slava.icxc.musicstudio.core.scan;

import java.nio.file.Path;

/** One scanned file: either read ({@link ScannedFile}) or not readable ({@link UnreadableFile}). */
public sealed interface ScanItem permits ScannedFile, UnreadableFile {

    /** @return the file's absolute path; never null */
    Path path();
}
