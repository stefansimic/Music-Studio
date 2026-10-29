package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Golden-file comparison. Run with {@code -Dgolden.update=true} to rewrite the files under
 * {@code src/test/resources/golden}; then review the diff by eye before committing.
 */
final class Golden {

    private static final Path DIR = Path.of("src/test/resources/golden");

    private Golden() {}

    static void assertMatches(String name, String actual) {
        try {
            if (Boolean.getBoolean("golden.update")) {
                Files.createDirectories(DIR);
                Files.writeString(DIR.resolve(name), actual, StandardCharsets.UTF_8);
                return;
            }
            try (InputStream in = Golden.class.getResourceAsStream("/golden/" + name)) {
                assertThat(in).as("golden file %s (create it with -Dgolden.update=true)", name).isNotNull();
                assertThat(actual).isEqualTo(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
