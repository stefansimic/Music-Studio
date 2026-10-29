package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * A genre name.
 *
 * @param name stripped, not blank, single line
 */
public record Genre(String name) {
    public Genre {
        Require.nonBlank(name, "name");
        Require.that(name.equals(name.strip()), "genre name must be stripped: '%s'", name);
        Require.that(name.indexOf('\n') < 0 && name.indexOf('\r') < 0, "genre name must be a single line");
    }
}
