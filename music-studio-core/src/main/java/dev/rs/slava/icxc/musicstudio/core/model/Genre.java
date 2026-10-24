package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * A genre name as plain text (never a numeric ID3v1 reference).
 *
 * @param name stripped (no leading/trailing whitespace), not blank, single line, no U+0000
 */
public record Genre(String name) {

    /**
     * Validates the name.
     *
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank, not stripped, or contains a line break or U+0000
     */
    public Genre {
        Require.nonBlank(name, "name");
        Require.that(name.equals(name.strip()), "genre name must be stripped: '%s'", name);
        Require.that(TextRules.isSingleLine(name), "genre name must be a single line");
        Require.that(name.indexOf('\0') < 0, "genre name must not contain U+0000");
    }
}
