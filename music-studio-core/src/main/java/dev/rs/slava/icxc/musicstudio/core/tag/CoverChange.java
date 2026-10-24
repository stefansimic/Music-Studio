package dev.rs.slava.icxc.musicstudio.core.tag;

/** What happens to the embedded cover. Remove and Replace arrive with story 009. */
public sealed interface CoverChange {

    /** The pictures stay as they are. */
    record Keep() implements CoverChange {
    }
}
