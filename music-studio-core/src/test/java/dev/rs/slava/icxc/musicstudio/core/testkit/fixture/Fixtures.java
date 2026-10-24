package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Copies fixtures out of the test-jar and checksums files. Fixtures are never used in place. */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * Copies a fixture into a directory under its own file name.
     *
     * @param fixture the fixture
     * @param dir     an existing directory (a {@code @TempDir})
     * @return the absolute path of the copy
     */
    public static Path copyTo(Fixture fixture, Path dir) {
        return copyTo(fixture, dir, fixture.fileName());
    }

    /**
     * Copies a fixture into a directory under another file name.
     *
     * @param fixture  the fixture
     * @param dir      an existing directory (a {@code @TempDir})
     * @param fileName the target file name
     * @return the absolute path of the copy
     */
    public static Path copyTo(Fixture fixture, Path dir, String fileName) {
        Path target = dir.resolve(fileName).toAbsolutePath();
        try (InputStream in = Fixtures.class.getResourceAsStream(fixture.resource())) {
            if (in == null) {
                throw new IllegalStateException("fixture resource missing: " + fixture.resource());
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    /**
     * @param fixture the fixture
     * @return the fixture's bytes
     */
    public static byte[] bytes(Fixture fixture) {
        try (InputStream in = Fixtures.class.getResourceAsStream(fixture.resource())) {
            if (in == null) {
                throw new IllegalStateException("fixture resource missing: " + fixture.resource());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param file a regular file
     * @return the lower-case hex SHA-256 of the file's content
     */
    public static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param data any bytes
     * @return the lower-case hex SHA-256
     */
    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
