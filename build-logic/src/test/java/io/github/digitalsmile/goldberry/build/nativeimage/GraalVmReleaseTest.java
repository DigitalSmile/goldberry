package io.github.digitalsmile.goldberry.build.nativeimage;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GraalVmRelease")
class GraalVmReleaseTest {

    /** The lines of a real GraalVM CE 25.3.4.1 `release` file that matter, in their order. */
    private static final String RELEASE_FILE = """
            IMPLEMENTOR="GraalVM Community"
            JAVA_RUNTIME_VERSION="25.0.4.1+1-jvmci-25.3-b22"
            JAVA_VERSION="25.0.4.1"
            JAVA_VERSION_DATE="2026-08-18"
            OS_NAME="Linux"
            GRAALVM_VERSION="25.3.4.1"
            """;

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("reads a four-part release")
        void readsARelease() {
            var release = GraalVmRelease.parse("25.3.4.1");
            assertAll(
                    () -> assertEquals(List.of(25, 3, 4, 1), release.components()),
                    () -> assertEquals("25.3", release.line()),
                    () -> assertEquals("25.3.4.1", release.toString()));
        }

        @ParameterizedTest(name = "refuses \"{0}\"")
        @ValueSource(strings = {"", "25", "25.", "v25.3", "25.3-dev", "jdk-25.0.2"})
        void refusesAnythingElse(String text) {
            assertThrows(IllegalArgumentException.class, () -> GraalVmRelease.parse(text));
        }
    }

    @Nested
    @DisplayName("the release file")
    class ReleaseFile {

        @Test
        @DisplayName("gives the GraalVM version, not the JDK's")
        void readsGraalVmVersion() {
            assertEquals(Optional.of(GraalVmRelease.parse("25.3.4.1")), GraalVmRelease.fromReleaseFile(RELEASE_FILE));
        }

        @Test
        @DisplayName("is empty for a JDK that is not a GraalVM")
        void emptyForAStockJdk() {
            assertEquals(Optional.empty(), GraalVmRelease.fromReleaseFile("""
                    IMPLEMENTOR="Eclipse Adoptium"
                    JAVA_VERSION="25.0.4"
                    """));
        }
    }

    @Nested
    @DisplayName("against the CI line")
    class AgainstCi {

        @Test
        @DisplayName("any 25.3 patch is on it, silently")
        void patchesAreOnTheLine() {
            assertAll(
                    () -> assertTrue(GraalVmRelease.parse("25.3.4.1").isCiLine()),
                    () -> assertTrue(GraalVmRelease.parse("25.3.9").mismatchWarning().isEmpty()));
        }

        @Test
        @DisplayName("another line is warned about, naming both")
        void otherLinesWarn() {
            var warning = GraalVmRelease.parse("25.2.4").mismatchWarning();
            assertAll(
                    () -> assertFalse(GraalVmRelease.parse("25.2.4").isCiLine()),
                    () -> assertTrue(warning.orElseThrow().contains("25.2.4")),
                    () -> assertTrue(warning.orElseThrow().contains(GraalVmRelease.CI_LINE)));
        }

        @Test
        @DisplayName("showcase.yml installs exactly the CI line, by GraalVM version")
        void workflowPinsTheLine() {
            var workflow = Repository.workflow("showcase.yml");
            var setup = workflow.substring(workflow.indexOf("uses: graalvm/setup-graalvm@"));
            var block = setup.substring(0, setup.indexOf("\n\n"));
            assertAll(
                    () -> assertTrue(Pattern.compile("version: '" + Pattern.quote(GraalVmRelease.CI_LINE) + "'")
                                    .matcher(block).find(),
                            "setup-graalvm must be pinned with version: '" + GraalVmRelease.CI_LINE
                                    + "' -- java-version alone resolves the old jdk-25.* tags"),
                    () -> assertTrue(block.contains("distribution: graalvm-community"), block));
        }
    }
}
