package dev.rs.slava.icxc.musicstudio.core.tag;

/**
 * What happens to the ID3v1 tag when a v1 text field contains letters that are not Latin (Greek, Serbian Cyrillic,
 * and so on). ID3v1 has no encoding marker, so there is no standard way to store them. The ID3v2 tag always keeps the
 * original text. A device profile chooses the policy.
 */
public enum V1NonLatinPolicy {
    /** No ID3v1 tag is written for that file (default); a warning is reported. Nothing is converted or garbled. */
    SKIP_V1,
    /**
     * ID3v1 bytes in Windows-1253 (Greek) or Windows-1251 (Cyrillic), one codepage per file. Correct only on devices
     * set to that locale. A file that needs both codepages falls back to {@link #SKIP_V1}.
     */
    CODEPAGE,
    /** ID3v1 holds a Latin transliteration (Greek ELOT 743 style, Serbian Cyrillic Gaj). */
    TRANSLITERATE
}
