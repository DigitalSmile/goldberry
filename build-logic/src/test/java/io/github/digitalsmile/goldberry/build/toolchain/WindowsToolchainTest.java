package io.github.digitalsmile.goldberry.build.toolchain;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WindowsToolchain")
class WindowsToolchainTest {

    @Test
    @DisplayName("pins both of CMake's compilers to the one cl, with forward slashes")
    void cmakeArguments() {
        var arguments = WindowsToolchain.cmakeArguments(Path.of("C:\\Program Files\\VS\\bin\\cl.exe"));
        assertAll(
                () -> assertEquals(2, arguments.size()),
                () -> assertTrue(arguments.get(0).startsWith("-DCMAKE_C_COMPILER="), arguments.get(0)),
                () -> assertTrue(arguments.get(1).startsWith("-DCMAKE_CXX_COMPILER="), arguments.get(1)),
                () -> assertTrue(arguments.stream().allMatch(a -> a.endsWith("cl.exe")), arguments.toString()),
                () -> assertTrue(arguments.stream().noneMatch(a -> a.contains("\\")), arguments.toString()));
    }

    @Nested
    @DisplayName("the message for a missing cl")
    class MissingCompiler {

        @Test
        @DisplayName("says how to get one, on a machine and in CI")
        void namesTheRemedies() {
            var message = WindowsToolchain.missingCompilerMessage(Optional.empty());
            assertAll(
                    () -> assertTrue(message.contains("Native Tools Command Prompt"), message),
                    () -> assertTrue(message.contains("vcvars64.bat"), message),
                    () -> assertTrue(message.contains(WindowsToolchain.CI_SETUP_ACTION), message),
                    () -> assertFalse(message.contains("Found instead"), message));
        }

        @Test
        @DisplayName("names the compiler CMake would have taken instead, and why that is worse")
        void namesTheStandIn() {
            var message = WindowsToolchain.missingCompilerMessage(Optional.of(Path.of("C:\\mingw64\\bin\\cc.exe")));
            assertAll(
                    () -> assertTrue(message.contains("Found instead: C:\\mingw64\\bin\\cc.exe"), message),
                    () -> assertTrue(message.contains("libgoldberry.dll"), message),
                    () -> assertTrue(message.contains("goldberry.dll"), message));
        }
    }

    @Nested
    @DisplayName("showcase.yml")
    class Workflow {

        private final String workflow = Repository.workflow("showcase.yml");

        @Test
        @DisplayName("puts cl on the PATH before it builds libgoldberry on Windows")
        void setsUpMsvcBeforeTheBuild() {
            var setup = workflow.indexOf("uses: " + WindowsToolchain.CI_SETUP_ACTION);
            var build = workflow.indexOf("name: " + WindowsToolchain.BUILD_STEP);
            assertAll(
                    () -> assertTrue(setup >= 0, "showcase.yml never runs " + WindowsToolchain.CI_SETUP_ACTION),
                    () -> assertTrue(build >= 0, "showcase.yml has no '" + WindowsToolchain.BUILD_STEP + "' step"),
                    () -> assertTrue(setup < build, "MSVC has to be set up before libgoldberry is built"));
        }

        @Test
        @DisplayName("and only on Windows, since the step has no meaning elsewhere")
        void onlyOnWindows() {
            var step = stepContaining("uses: " + WindowsToolchain.CI_SETUP_ACTION);
            assertTrue(step.contains("if: runner.os == 'Windows'"), step);
        }

        /** The YAML list item -- from its {@code - } to the next -- holding {@code text}. */
        private String stepContaining(String text) {
            var items = List.of(Pattern.compile("(?m)^      - ").split(workflow));
            return items.stream().filter(item -> item.contains(text)).findFirst()
                    .orElseThrow(() -> new AssertionError("no step contains " + text));
        }
    }
}
