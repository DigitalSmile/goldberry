package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoldberryTest {

    /// YEAR.COUNT, an optional patch, an optional -SNAPSHOT (ADR-0333) -- the shape
    /// `CalendarVersion` accepts in build-logic.
    private static final String CALENDAR_VERSION = "\\d{4}\\.[1-9]\\d*(\\.[1-9]\\d*)?(-SNAPSHOT)?";

    @Test
    @DisplayName("version() is populated from the generated build info")
    void versionComesFromTheBuild() {
        var version = Goldberry.version();

        assertNotNull(version);
        assertFalse(version.isBlank(), "version must not be blank");
    }

    @Test
    @DisplayName("version() is a calendar version, as a snapshot or a release")
    void versionIsMavenCompatible() {
        var version = Goldberry.version();

        // Not semver's three parts: `2026.1` is a release, and a three-part
        // pattern failed every build after the switch.
        assertTrue(version.matches(CALENDAR_VERSION), () -> "not a calendar version: " + version);
    }

    @Test
    @DisplayName("the calendar pattern still refuses what is not one")
    void calendarPatternRefusesOtherShapes() {
        var pattern = CALENDAR_VERSION;
        assertAll(
                () -> assertTrue("2026.1".matches(pattern)),
                () -> assertTrue("2026.1-SNAPSHOT".matches(pattern)),
                () -> assertTrue("2026.2.1".matches(pattern)),
                () -> assertFalse("0.1.0-SNAPSHOT".matches(pattern)),
                () -> assertFalse("2026.0".matches(pattern)),
                () -> assertFalse("2026".matches(pattern)));
    }

    @Test
    @DisplayName("version() is the version the build resolved, and is not a placeholder")
    void versionIsTheBuilds() {
        // `assertTrue(version().equals(version()))` stood here, which compares a
        // static final with itself and can never fail (the 2026-09-18 review, §6).
        // What is worth pinning is that the generated constant reached the class:
        // an unresolved one reads `unknown`, which is what a jar built outside
        // Gradle would report.
        var version = Goldberry.version();
        assertNotNull(version);
        assertNotEquals("unknown", version, "the build's version never reached BuildInfo");
        assertTrue(version.matches("\\d{4}\\.\\d+(\\.\\d+)?(-SNAPSHOT)?"), () -> "not a calendar version: " + version);
    }
}
