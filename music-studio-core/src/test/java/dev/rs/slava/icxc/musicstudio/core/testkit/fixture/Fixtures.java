package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Copies fixtures out of the test-jar. Tests never touch a fixture in place; they always work on a copy. */
public final class Fixtures {

    private Fixtures() {}

    /** @return a new copy of {@code fixture} in {@code dir}, with the fixture's own file name */
    public static Path copyTo(Fixture fixture, Path dir) {
        return copyTo(fixture, dir, fixture.fileName());
    }

    /** @return a new copy of {@code fixture} in {@code dir} named {@code fileName} */
    public static Path copyTo(Fixture fixture, Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        try (InputStream in = open(fixture)) {
            Files.copy(in, target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the fixture's bytes */
    public static byte[] bytes(Fixture fixture) {
        try (InputStream in = open(fixture)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(Fixture fixture) {
        InputStream in = Fixtures.class.getResourceAsStream("/fixtures/mp3/" + fixture.fileName());
        return Objects.requireNonNull(in, "missing fixture resource " + fixture.fileName());
    }

    /** @return lower-case hex SHA-256 of the file content */
    public static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return lower-case hex SHA-256 of the bytes */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
