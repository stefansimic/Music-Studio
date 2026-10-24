package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Arrays;

/**
 * The bytes of an embedded picture. Byte arrays are copied on the way in and out; equality is by content.
 *
 * @param data     the picture data; not null, not empty
 * @param mimeType e.g. "image/jpeg"; not blank
 * @param type     the picture's role; not null
 */
public record CoverArt(byte[] data, String mimeType, PictureType type) {

    /**
     * Validates and copies the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if {@code data} is empty or {@code mimeType} is blank
     */
    public CoverArt {
        Require.nonNull(data, "data");
        Require.that(data.length > 0, "data must not be empty");
        Require.nonBlank(mimeType, "mimeType");
        Require.nonNull(type, "type");
        data = data.clone();
    }

    /** @return a copy of the picture data; never null */
    @Override
    public byte[] data() {
        return data.clone();
    }

    /** @return the size of the picture data in bytes */
    public int sizeBytes() {
        return data.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoverArt that
                && Arrays.equals(data, that.data) && mimeType.equals(that.mimeType) && type == that.type;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(data) + mimeType.hashCode()) + type.hashCode();
    }

    @Override
    public String toString() {
        return "CoverArt[" + mimeType + ", " + type + ", " + data.length + " bytes]";
    }
}
