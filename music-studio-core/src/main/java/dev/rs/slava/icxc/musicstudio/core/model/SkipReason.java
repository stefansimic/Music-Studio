package dev.rs.slava.icxc.musicstudio.core.model;

/** Why a file or directory was deliberately not scanned. */
public enum SkipReason {
    /** Name starts with "." (incl. macOS "._*"), or the Windows hidden attribute is set. */
    HIDDEN,
    /** A symbolic link to a directory; never followed. */
    DIRECTORY_SYMLINK,
    /** Listed in {@code ListingOptions.excludedDirectories()}. */
    EXCLUDED,
    /** A symbolic link whose target does not exist; shown so the user sees why a file is missing. */
    BROKEN_SYMLINK
}
