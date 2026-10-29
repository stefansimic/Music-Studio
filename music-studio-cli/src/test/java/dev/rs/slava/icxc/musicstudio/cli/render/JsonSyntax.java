package dev.rs.slava.icxc.musicstudio.cli.render;

/** A strict JSON syntax checker (RFC 8259), to prove that the hand-written JSON output is well-formed. */
public final class JsonSyntax {

    private final String text;
    private int pos;

    private JsonSyntax(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException with the position if {@code text} is not exactly one JSON value */
    public static void assertValid(String text) {
        JsonSyntax parser = new JsonSyntax(text);
        parser.whitespace();
        parser.value();
        parser.whitespace();
        if (parser.pos != text.length()) {
            throw parser.error("trailing content");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("invalid JSON at " + pos + ": " + message + " near '"
                + text.substring(Math.max(0, pos - 20), Math.min(text.length(), pos + 20)) + "'");
    }

    private void whitespace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private void expect(char c) {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private void value() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> number();
        }
    }

    private void literal(String word) {
        if (!text.startsWith(word, pos)) {
            throw error("expected " + word);
        }
        pos += word.length();
    }

    private void object() {
        expect('{');
        whitespace();
        if (text.charAt(pos) == '}') {
            pos++;
            return;
        }
        while (true) {
            whitespace();
            string();
            whitespace();
            expect(':');
            whitespace();
            value();
            whitespace();
            if (pos < text.length() && text.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect('}');
            return;
        }
    }

    private void array() {
        expect('[');
        whitespace();
        if (text.charAt(pos) == ']') {
            pos++;
            return;
        }
        while (true) {
            whitespace();
            value();
            whitespace();
            if (pos < text.length() && text.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect(']');
            return;
        }
    }

    private void string() {
        expect('"');
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return;
            }
            if (c < 0x20) {
                throw error("unescaped control character");
            }
            if (c == '\\') {
                char escape = text.charAt(pos++);
                if (escape == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (Character.digit(text.charAt(pos++), 16) < 0) {
                            throw error("bad \\u escape");
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escape) < 0) {
                    throw error("bad escape");
                }
            }
        }
        throw error("unterminated string");
    }

    private void number() {
        int start = pos;
        if (pos < text.length() && text.charAt(pos) == '-') {
            pos++;
        }
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
            pos++;
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        if (pos == start || text.substring(start, pos).equals("-")) {
            throw error("expected a value");
        }
    }
}
