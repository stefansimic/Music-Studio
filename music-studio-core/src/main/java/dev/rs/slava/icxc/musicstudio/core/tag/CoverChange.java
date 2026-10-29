package dev.rs.slava.icxc.musicstudio.core.tag;

/** What happens to the cover art. Only {@link Keep} exists until story 009. */
public sealed interface CoverChange {

    /** The cover art of the source stays as it is. */
    record Keep() implements CoverChange {}
}
