package dev.rs.slava.icxc.musicstudio.core.model;

/** Stable codes so frontends can render them and tests can assert without string matching. */
public enum IssueCode {
    READ_FAILED,
    TAG_DAMAGED,
    READ_VALUE_INVALID,
    LISTING_ACCESS_DENIED,
    ROOT_NOT_FOUND,
    V1_V2_CONFLICT
}
