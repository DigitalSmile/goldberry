package io.github.digitalsmile.goldberry.build.version;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import io.github.digitalsmile.goldberry.build.version.BuildVersion.Release;
import io.github.digitalsmile.goldberry.build.version.BuildVersion.Snapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BuildVersion")
class BuildVersionTest {

    @Nested
    @DisplayName("outside a release")
    class OutsideARelease {

        @Test
        @DisplayName("is a snapshot of the declared line")
        void isASnapshot() {
            var version = BuildVersion.resolve("2026.1", false, null);
            assertAll(
                    () -> assertInstanceOf(Snapshot.class, version),
                    () -> assertEquals("2026.1-SNAPSHOT", version.toString()),
                    () -> assertTrue(version.isSnapshot()),
                    () -> assertEquals("v2026.1", version.tag()));
        }

        @Test
        @DisplayName("treats a blank tag as no tag -- which is what an unset workflow input is")
        void blankTagIsNoTag() {
            assertInstanceOf(Snapshot.class, BuildVersion.resolve("2026.1", false, " "));
        }

        @Test
        @DisplayName("refuses a tag, which only means something at release")
        void refusesATag() {
            assertThrows(IllegalArgumentException.class, () -> BuildVersion.resolve("2026.1", false, "v2026.1"));
        }

        @Test
        @DisplayName("refuses a declared -SNAPSHOT, so the suffix has one author")
        void refusesADeclaredSnapshot() {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> BuildVersion.resolve("2026.1-SNAPSHOT", false, null));
            assertTrue(error.getMessage().contains("declare the release line alone"), error.getMessage());
        }
    }

    @Nested
    @DisplayName("at a release")
    class AtARelease {

        @Test
        @DisplayName("is the line itself when the tag matches")
        void isTheLine() {
            var version = BuildVersion.resolve("2026.2.1", true, "v2026.2.1");
            assertAll(
                    () -> assertInstanceOf(Release.class, version),
                    () -> assertEquals("2026.2.1", version.toString()),
                    () -> assertFalse(version.isSnapshot()));
        }

        @Test
        @DisplayName("needs a tag")
        void needsATag() {
            var error = assertThrows(IllegalArgumentException.class, () -> BuildVersion.resolve("2026.1", true, null));
            assertTrue(error.getMessage().contains("-Pgoldberry.releaseTag=v2026.1"), error.getMessage());
        }

        @Test
        @DisplayName("refuses a tag that names another version, before anything is published")
        void refusesAMismatch() {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> BuildVersion.resolve("2026.1", true, "v2026.2"));
            assertTrue(error.getMessage().contains("does not match goldberryVersion=2026.1"), error.getMessage());
        }

        @Test
        @DisplayName("refuses a tag without its prefix")
        void refusesAnUnprefixedTag() {
            assertThrows(IllegalArgumentException.class, () -> BuildVersion.resolve("2026.1", true, "2026.1"));
        }

        @Test
        @DisplayName("refuses a tag that is not a version at all")
        void refusesANonVersionTag() {
            assertThrows(IllegalArgumentException.class, () -> BuildVersion.resolve("2026.1", true, "v2026.1-rc1"));
        }
    }

    @Test
    @DisplayName("gradle.properties declares a line this resolves")
    void theRepositoryDeclaresALine() throws IOException {
        var properties = new Properties();
        properties.load(new StringReader(Repository.read("gradle.properties")));
        var declared = properties.getProperty("goldberryVersion");
        assertDoesNotThrow(() -> BuildVersion.resolve(declared, false, null),
                "goldberryVersion=" + declared + " must be a bare calendar version (ADR-0333)");
    }
}
