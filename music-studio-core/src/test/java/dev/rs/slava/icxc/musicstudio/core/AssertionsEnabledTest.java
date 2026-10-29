package dev.rs.slava.icxc.musicstudio.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Fails the build if {@code -ea} is ever lost: asserts are part of the test oracle. */
class AssertionsEnabledTest {

    @Test
    void assertionsAreEnabled() {
        assertThatThrownBy(() -> {
            assert false;
        }).isInstanceOf(AssertionError.class);
    }
}
