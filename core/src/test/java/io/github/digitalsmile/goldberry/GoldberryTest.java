package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoldberryTest {

    @Test
    @DisplayName("version() is populated from the generated build info")
    void versionComesFromTheBuild() {
        var version = Goldberry.version();

        assertNotNull(version);
        assertFalse(version.isBlank(), "version must not be blank");
    }

    @Test
    @DisplayName("version() is a Maven-compatible version string")
    void versionIsMavenCompatible() {
        var version = Goldberry.version();

        assertTrue(
                version.matches("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?"),
                () -> "not a Maven-compatible version: " + version);
    }

    @Test
    @DisplayName("version() is stable across calls")
    void versionIsStable() {
        assertTrue(Goldberry.version().equals(Goldberry.version()));
    }
}
