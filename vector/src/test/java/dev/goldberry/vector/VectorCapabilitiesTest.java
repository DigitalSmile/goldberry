package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VectorCapabilities")
class VectorCapabilitiesTest {

    @BeforeEach
    void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("says the library loaded, with no reason against it")
    void available() {
        assertTrue(VectorCapabilities.isAvailable());
        assertTrue(VectorCapabilities.unavailableReason().isEmpty());
    }

    @Test
    @DisplayName("names the ThorVG it holds and how it was built")
    void versions() {
        var line = VectorCapabilities.versions().orElseThrow();
        assertTrue(line.startsWith("ThorVG 1.1.4; cpu; svg png jpg; simd "), line);
    }
}
