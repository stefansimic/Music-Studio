package dev.rs.slava.icxc.musicstudio.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Fails the build if tests ever run without {@code -ea}: assertions are part of the test oracle. */
class AssertionsEnabledTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    void assertionsAreEnabled() {
        assertThatThrownBy(() -> {
            assert false;
        }).isInstanceOf(AssertionError.class);
    }
}
