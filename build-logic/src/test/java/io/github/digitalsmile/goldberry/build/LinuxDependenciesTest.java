package io.github.digitalsmile.goldberry.build;

import io.github.digitalsmile.goldberry.build.LinuxDependencies.Dependency;
import io.github.digitalsmile.goldberry.build.LinuxDependencies.Necessity;
import io.github.digitalsmile.goldberry.build.LinuxDependencies.PackageManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LinuxDependencies}.
 *
 * <p>Two kinds. The first is ordinary: does a row map to the right package, does
 * an install command deduplicate. The second reads {@code .github/workflows} and
 * asserts the table and CI agree — because the bug this class exists for was
 * exactly that disagreement. CI had been taught twice, by two failed builds, that
 * XScrnSaver and XTest are hard dependencies of SDL's X11 driver; it wrote that
 * down in a comment; and the local toolchain check, which exists to say the same
 * thing before the build starts, still called one of them optional under a
 * pkg-config module name that does not exist. See ADR-0082.
 */
@DisplayName("LinuxDependencies")
class LinuxDependenciesTest {

    /**
     * The repository root. Handed over by {@code build-logic/build.gradle} so the
     * test does not have to guess; found by walking up when it is absent, which is
     * what happens when the test is run straight from an IDE.
     */
    private static Path repositoryRoot() {
        var declared = System.getProperty("goldberry.repoRoot");
        if (declared != null && !declared.isBlank()) {
            return Path.of(declared);
        }
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve(".github/workflows"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        // Deliberately not `assumeTrue`. A drift guard that skips when it cannot
        // find what it guards is a green tick over an unchecked invariant, which
        // is the one outcome worse than a red one.
        throw new IllegalStateException(
                "cannot find .github/workflows above " + Path.of("").toAbsolutePath()
                        + "; set -Dgoldberry.repoRoot=<repo>");
    }

    private static String workflow(String name) {
        var file = repositoryRoot().resolve(".github/workflows").resolve(name);
        assertTrue(Files.isRegularFile(file), file + " does not exist");
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Whether a package is named in a workflow, as a whole word.
     *
     * <p>Whole-word, because {@code wayland-devel} is a substring of nothing here
     * today and would be of {@code libwayland-devel} tomorrow, and a guard that
     * passes on a near-miss is not a guard.
     */
    private static boolean installs(String workflowText, String packageName) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(packageName) + "(?![\\w.-])")
                .matcher(workflowText)
                .find();
    }

    @Nested
    @DisplayName("the table")
    class Table {

        @Test
        @DisplayName("names every pkg-config module exactly once")
        void namesEveryModuleOnce() {
            var seen = new HashSet<String>();
            var duplicates = LinuxDependencies.ALL.stream()
                    .map(Dependency::module)
                    .filter(module -> !seen.add(module))
                    .toList();
            assertTrue(duplicates.isEmpty(), "duplicated modules: " + duplicates);
        }

        @Test
        @DisplayName("does not list XScrnSaver as `xss`, a module no distribution ships")
        void doesNotUseTheBogusXssModule() {
            // The original bug. `pkg-config --exists xss` is false on a machine with
            // libxss-dev installed and on one without it, so the row reported the
            // same answer either way -- and the answer it reported was the wrong one.
            // SDL asks for `xscrnsaver` (cmake/sdlchecks.cmake, Xss_PKG_CONFIG_SPEC).
            assertTrue(LinuxDependencies.ALL.stream().noneMatch(d -> d.module().equals("xss")),
                    "the pkg-config module for libxss-dev is `xscrnsaver`, not `xss`");
            assertEquals("libxss-dev", LinuxDependencies.byModule("xscrnsaver").aptPackage());
            assertEquals("libXScrnSaver-devel", LinuxDependencies.byModule("xscrnsaver").dnfPackage());
        }

        @ParameterizedTest(name = "{0} stops the configure")
        @ValueSource(strings = {"x11", "xext", "xrandr", "xcursor", "xi", "xfixes", "xscrnsaver", "xtst"})
        @DisplayName("marks SDL's X11 sub-features as hard stops, not as optional extras")
        void marksTheX11SubFeaturesAsHardStops(String module) {
            // Every one of these is a `dep_option(SDL_X11_* ... ON ...)` with an
            // `SDL_missing_dependency` branch. There is no degraded build behind
            // any of them; there is a FATAL_ERROR.
            assertEquals(Necessity.HARD_STOP, LinuxDependencies.byModule(module).necessity());
        }

        @Test
        @DisplayName("requires everything SDL's Wayland check asks for in one pkg_check_modules")
        void requiresTheWholeWaylandSpec() {
            // CheckWayland is a single pkg_check_modules over five specs. Lose one
            // and the driver is not built, the configure still succeeds, and nobody
            // finds out until a Wayland session has no window.
            for (var module : List.of("wayland-client", "wayland-egl", "wayland-cursor", "egl", "xkbcommon")) {
                assertTrue(LinuxDependencies.byModule(module).required(),
                        module + " is part of SDL's Wayland pkg-config spec and must be required");
            }
        }

        @Test
        @DisplayName("requires libdecor, without which a Wayland window has no titlebar")
        void requiresLibdecor() {
            // The one dependency whose absence is invisible in every log and shows
            // up only as a window with no close button that cannot be resized.
            // GNOME's compositor declines server-side decorations, so on GNOME
            // libdecor is not a fallback -- it is the only implementation.
            var libdecor = LinuxDependencies.byModule("libdecor-0");
            assertEquals(Necessity.NEEDED, libdecor.necessity());
            assertEquals("libdecor-0-dev", libdecor.aptPackage());
            // Not a HARD_STOP: SDL configures happily without it. That is the
            // problem, not a reason to relax the row.
            assertFalse(libdecor.describedPurpose().contains("stops the configure"));
        }

        @Test
        @DisplayName("keeps the features the toolkit does not use optional")
        void keepsUnusedFeaturesOptional() {
            for (var module : List.of("alsa", "libpulse", "libdrm", "gbm", "libudev", "dbus-1")) {
                assertFalse(LinuxDependencies.byModule(module).required(),
                        module + " is not something Goldberry uses; it must not fail a build");
            }
        }

        @Test
        @DisplayName("rejects a row with a blank field")
        void rejectsABlankField() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Dependency(" ", "a", "b", Necessity.NEEDED, "c")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Dependency("m", "", "b", Necessity.NEEDED, "c")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Dependency("m", "a", "b", Necessity.NEEDED, "")),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Dependency("m", "a", "b", null, "c")));
        }

        @Test
        @DisplayName("has no row for an unknown module")
        void hasNoRowForAnUnknownModule() {
            assertThrows(IllegalArgumentException.class, () -> LinuxDependencies.byModule("libnope"));
        }
    }

    @Nested
    @DisplayName("installCommand")
    class InstallCommand {

        @Test
        @DisplayName("names the distribution's package, not the pkg-config module")
        void namesTheDistributionPackage() {
            var xscrnsaver = LinuxDependencies.byModule("xscrnsaver");
            assertEquals("sudo apt install libxss-dev",
                    LinuxDependencies.installCommand(List.of(xscrnsaver), PackageManager.APT));
            assertEquals("sudo dnf install -y libXScrnSaver-devel",
                    LinuxDependencies.installCommand(List.of(xscrnsaver), PackageManager.DNF));
        }

        @Test
        @DisplayName("collapses the four Wayland modules into one package")
        void collapsesRepeatedPackages() {
            var wayland = List.of(
                    LinuxDependencies.byModule("wayland-client"),
                    LinuxDependencies.byModule("wayland-cursor"),
                    LinuxDependencies.byModule("wayland-egl"));
            assertEquals("sudo apt install libwayland-dev",
                    LinuxDependencies.installCommand(wayland, PackageManager.APT));
        }

        @Test
        @DisplayName("sorts the packages, so two runs read the same")
        void sortsThePackages() {
            var absent = List.of(
                    LinuxDependencies.byModule("xtst"),
                    LinuxDependencies.byModule("xscrnsaver"),
                    LinuxDependencies.byModule("egl"));
            assertEquals("sudo apt install libegl1-mesa-dev libxss-dev libxtst-dev",
                    LinuxDependencies.installCommand(absent, PackageManager.APT));
        }

        @Test
        @DisplayName("is empty when nothing is missing")
        void isEmptyWhenNothingIsMissing() {
            assertEquals("", LinuxDependencies.installCommand(List.of(), PackageManager.APT));
        }

        @Test
        @DisplayName("follows dnf when dnf is what the machine has")
        void followsTheDetectedManager() {
            assertEquals(PackageManager.DNF, LinuxDependencies.managerFor(true));
            assertEquals(PackageManager.APT, LinuxDependencies.managerFor(false));
        }
    }

    @Nested
    @DisplayName("the failure message")
    class FailureMessage {

        @Test
        @DisplayName("says a hard stop is a hard stop, and how to fix it")
        void reportsAHardStop() {
            var message = LinuxDependencies.missingRequiredMessage(
                    List.of(LinuxDependencies.byModule("xscrnsaver"),
                            LinuxDependencies.byModule("xtst")),
                    PackageManager.APT);

            assertAll(
                    () -> assertTrue(message.contains("xscrnsaver -- SDL3 screensaver inhibition"), message),
                    () -> assertTrue(message.contains("SDL stops the configure without it"), message),
                    () -> assertTrue(message.contains("sudo apt install libxss-dev libxtst-dev"), message));
        }

        @Test
        @DisplayName("does not claim a silent drop-out will stop the configure")
        void doesNotOverstateASilentDropOut() {
            var message = LinuxDependencies.missingRequiredMessage(
                    List.of(LinuxDependencies.byModule("egl")), PackageManager.DNF);

            assertAll(
                    () -> assertTrue(message.contains("egl -- SDL3 Wayland backend"), message),
                    () -> assertFalse(message.contains("stops the configure"), message),
                    () -> assertTrue(message.contains("sudo dnf install -y mesa-libEGL-devel"), message));
        }

        @Test
        @DisplayName("warns rather than instructs for an optional dependency")
        void warnsForAnOptionalDependency() {
            var message = LinuxDependencies.missingOptionalMessage(
                    List.of(LinuxDependencies.byModule("alsa")), PackageManager.APT);

            assertAll(
                    () -> assertTrue(message.startsWith("WARNING:"), message),
                    () -> assertTrue(message.contains("alsa -- SDL3 audio"), message),
                    () -> assertTrue(message.contains("sudo apt install libasound2-dev"), message));
        }

        @Test
        @DisplayName("refuses to report a failure with nothing missing")
        void refusesAnEmptyReport() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LinuxDependencies.missingRequiredMessage(List.of(), PackageManager.APT)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LinuxDependencies.missingOptionalMessage(List.of(), PackageManager.APT)));
        }
    }

    /**
     * The guard that would have caught the bug. The table and the CI workflows are
     * two statements of the same fact, and the first time they disagreed the
     * disagreement cost two failed builds -- one per missing package, because they
     * fail in order and fixing the first only reveals the second.
     */
    @Nested
    @DisplayName("agrees with CI")
    class AgreesWithCi {

        /**
         * The workflows that build natively through Gradle, and therefore run
         * {@code checkToolchain}. A required dependency the check would reject has
         * to be installed here, or CI fails on its own preflight.
         */
        private static final List<String> APT_WORKFLOWS = List.of("example.yml", "showcase.yml");

        /**
         * The release leg. It runs CMake directly inside a manylinux container with
         * no JDK, so {@code checkToolchain} never runs and nothing warns it: the
         * only thing standing between it and SDL's {@code FATAL_ERROR} is this list
         * being right.
         */
        private static final String DNF_WORKFLOW = "linux.yml";

        @ParameterizedTest(name = "{0} installs every required package")
        @ValueSource(strings = {"example.yml", "showcase.yml"})
        @DisplayName("the Gradle-driven workflows install what checkToolchain requires")
        void gradleWorkflowsInstallEveryRequiredPackage(String name) {
            var text = workflow(name);
            var absent = LinuxDependencies.required().stream()
                    .filter(dependency -> !installs(text, dependency.aptPackage()))
                    .map(dependency -> dependency.module() + " (" + dependency.aptPackage() + ")")
                    .toList();
            assertTrue(absent.isEmpty(),
                    name + " does not install: " + absent
                            + " -- checkToolchain would fail the build before CMake ran");
        }

        @Test
        @DisplayName("the manylinux workflow installs every package SDL will not configure without")
        void theContainerWorkflowInstallsEveryHardStop() {
            var text = workflow(DNF_WORKFLOW);
            var absent = LinuxDependencies.hardStops().stream()
                    .filter(dependency -> !installs(text, dependency.dnfPackage()))
                    .map(dependency -> dependency.module() + " (" + dependency.dnfPackage() + ")")
                    .toList();
            assertTrue(absent.isEmpty(),
                    DNF_WORKFLOW + " does not install: " + absent
                            + " -- SDL's CheckX11 stops the configure without them");
        }

        @Test
        @DisplayName("covers the two hard dependencies CI learned about the expensive way")
        void coversTheTwoThatBrokeCi() {
            // Named individually rather than left to the sweep above: these two are
            // why the guard exists, and a sweep that someone later narrows should
            // still fail if either goes missing.
            for (var name : APT_WORKFLOWS) {
                var text = workflow(name);
                assertTrue(installs(text, "libxss-dev"), name + " must install libxss-dev");
                assertTrue(installs(text, "libxtst-dev"), name + " must install libxtst-dev");
            }
            var container = workflow(DNF_WORKFLOW);
            assertTrue(installs(container, "libXScrnSaver-devel"),
                    DNF_WORKFLOW + " must install libXScrnSaver-devel");
            assertTrue(installs(container, "libXtst-devel"),
                    DNF_WORKFLOW + " must install libXtst-devel");
        }
    }
}
