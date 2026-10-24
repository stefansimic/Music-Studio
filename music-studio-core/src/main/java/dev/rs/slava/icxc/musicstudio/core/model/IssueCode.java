package dev.rs.slava.icxc.musicstudio.core.model;

/** Stable issue codes, so frontends can render/localise and tests can assert without string matching. */
public enum IssueCode {
    // read / scan
    /** A file could not be read at all. */
    READ_FAILED,
    /** A tag is damaged; readable layers were still returned. */
    TAG_DAMAGED,
    /** A stored value does not fit its field type (e.g. track "A1") and was ignored. */
    READ_VALUE_INVALID,
    /** A directory could not be listed. */
    LISTING_ACCESS_DENIED,
    /** The folder to scan does not exist or is not a directory. */
    ROOT_NOT_FOUND,
    /** An ID3v1 value differs from the ID3v2 value. */
    V1_V2_CONFLICT,
    /** The tags are readable but no playable audio stream was found; the file may not play on a device. */
    NO_AUDIO_STREAM,
    // extract
    /** A name did not match the input pattern. */
    NO_MATCH,
    /** A name matched the input pattern in more than one way. */
    AMBIGUOUS_MATCH,
    /** An extracted value does not fit its field. */
    VALUE_INVALID_FOR_FIELD,
    /** A track/disc number exceeds its total. */
    POSITION_EXCEEDS_TOTAL,
    /** Two files of one folder get the same track number. */
    DUPLICATE_TRACK_NUMBER,
    // write options / tags
    /** The tag writer cannot write the requested version. */
    UNSUPPORTED_WRITE_VERSION,
    /** The format has no slot for a field. */
    UNSUPPORTED_FIELD,
    /** A value was transliterated for ID3v1. */
    V1_TRANSLITERATED,
    /** A value was truncated for ID3v1. */
    V1_TRUNCATED,
    /** Characters could not be represented in ID3v1 and were dropped. */
    V1_CHAR_DROPPED,
    /** ID3v1 has no slot for a field (album artist, disc). */
    V1_FIELD_NOT_REPRESENTABLE,
    /** A value cannot be encoded in the selected ID3v2 encoding. */
    V2_NOT_ENCODABLE,
    /** A frame has no equivalent in the target version. */
    FRAME_NOT_REPRESENTABLE,
    /** An existing tag of a family not selected for writing was kept. */
    OTHER_VERSION_KEPT,
    /** An existing tag of a family not selected for writing was removed. */
    OTHER_VERSION_REMOVED,
    // export
    /** A fallback value was used in the output pattern. */
    FALLBACK_USED,
    /** Characters of a file name were replaced. */
    NAME_SANITIZED,
    /** The target path is too long. */
    PATH_TOO_LONG,
    /** A name collision was resolved with a suffix. */
    COLLISION_SUFFIXED,
    /** A file was skipped because of a name collision. */
    COLLISION_SKIPPED,
    /** The target file already exists. */
    TARGET_EXISTS,
    /** The target is the source file itself. */
    TARGET_IS_SOURCE,
    /** The output folder lies inside the source folder. */
    OUTPUT_INSIDE_SOURCE,
    /** Not enough free space on the target. */
    INSUFFICIENT_SPACE,
    /** A file is too large for the target file system. */
    FILE_TOO_LARGE_FOR_TARGET_FS,
    /** The source changed since the plan was made. */
    SOURCE_CHANGED,
    /** Writing a file failed. */
    WRITE_FAILED,
    /** The written file does not contain what was planned. */
    VERIFY_MISMATCH,
    /** The operation was cancelled. */
    CANCELLED,
    // undo
    /** An exported file changed since the export. */
    UNDO_FILE_CHANGED,
    /** An exported file no longer exists. */
    UNDO_FILE_MISSING,
    /** A created directory is not empty and was kept. */
    UNDO_DIR_NOT_EMPTY
}
