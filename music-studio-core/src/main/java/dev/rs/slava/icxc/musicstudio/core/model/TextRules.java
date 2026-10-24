package dev.rs.slava.icxc.musicstudio.core.model;

/** Shared text checks for the model. */
final class TextRules {

    private TextRules() {
    }

    static boolean isSingleLine(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == ' ' || c == ' ') {
                return false;
            }
        }
        return true;
    }
}
