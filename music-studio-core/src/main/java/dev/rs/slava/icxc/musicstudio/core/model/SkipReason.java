package dev.rs.slava.icxc.musicstudio.core.model;

/** Why a file or directory was deliberately not scanned. */
public enum SkipReason {
    HIDDEN,
    DIRECTORY_SYMLINK,
    EXCLUDED
}
