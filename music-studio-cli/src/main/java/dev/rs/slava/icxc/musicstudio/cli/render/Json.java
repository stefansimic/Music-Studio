package dev.rs.slava.icxc.musicstudio.cli.render;

/** The little JSON that the scan output needs, written by hand (no library, no reflection). */
final class Json {

    private Json() {}

    /** @return {@code value} as a JSON string literal, quotes included; control characters are escaped */
    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    boolean control = c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029;
                    boolean loneSurrogate = Character.isHighSurrogate(c)
                            ? !(i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1)))
                            : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(value.charAt(i - 1)));
                    if (control || loneSurrogate) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
