package io.github.digitalsmile.goldberry.natives.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.natives.GoldberryShim;

/// The build's half of `docs/gaps.md` G32, read as text.
///
/// The capability bits are verified against a compiled library by the layout
/// probe, and the Java side of the word by [NativeCapabilityTest]. Neither can
/// say anything about a machine where the superbuild has not run — and the defect
/// G32 records lived exactly there, in a configure that quietly produced a
/// library which could not ask the desktop anything.
///
/// So this reads the build files, like [io.github.digitalsmile.goldberry.natives.SuperbuildTest]
/// does for the clone progress: the probe exists, it stops rather than degrading,
/// the function is exported, and the two ends of the ABI agree. All four are
/// invisible from Java otherwise, and all four are one careless edit from being
/// undone.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("the platform-integration build")
class PlatformIntegrationBuildTest {

    private final Path cmakeDir = locateCmakeDir();
    private final String cmakeLists = read(cmakeDir.resolve("CMakeLists.txt"));
    private final String shim = read(cmakeDir.resolve("goldberry_shim.c"));
    private final String symbols = read(cmakeDir.resolve("exports/goldberry.symbols"));

    /// `:natives/src/main/cmake`, from wherever the tests were started.
    ///
    /// Gradle runs them with the project directory as the working directory; an
    /// IDE may use the repository root. [io.github.digitalsmile.goldberry.natives.SuperbuildTest]
    /// makes the same two guesses.
    private static Path locateCmakeDir() {
        var working = Path.of(System.getProperty("user.dir"));
        var own = working.resolve("src/main/cmake");
        return Files.isRegularFile(own.resolve("CMakeLists.txt")) ? own : working.resolve("natives/src/main/cmake");
    }

    private static String read(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    private void assumeReadable() {
        assertTrue(
                !cmakeLists.isEmpty() && !shim.isEmpty() && !symbols.isEmpty(),
                "the superbuild's sources are not readable from " + cmakeDir);
    }

    @ParameterizedTest
    @EnumSource(Integration.class)
    @DisplayName("the superbuild probes for each desktop integration by SDL's own module names")
    void probesForEveryIntegration(Integration integration) {
        assumeReadable();

        for (var module : integration.modules()) {
            assertTrue(
                    cmakeLists.contains(module),
                    "the superbuild does not probe for the pkg-config module " + module
                            + ", so a library built without it would report "
                            + integration.capability() + " wrongly");
        }
        assertTrue(
                cmakeLists.contains(integration.define()), "nothing passes " + integration.define() + " to the shim");
        assertTrue(
                shim.contains(integration.define()),
                "the shim does not read " + integration.define() + ", so the probe's answer is discarded");
        assertTrue(
                cmakeLists.contains(integration.sdlBuildConfig()),
                "the probe is never cross-checked against SDL's own " + integration.sdlBuildConfig());
    }

    @Test
    @DisplayName("a configure with no D-Bus stops instead of degrading")
    void dbusStopsTheConfigure() {
        assumeReadable();

        // The whole of G32 in one property. Everything else here is diagnostics;
        // this is the line that keeps the published library able to ask the
        // desktop what it is set to.
        assertTrue(
                cmakeLists.contains("GOLDBERRY_REQUIRE_PLATFORM_INTEGRATION"),
                "there is no option governing whether a missing integration stops the build");
        assertTrue(
                cmakeLists.contains("REQUIRED)"),
                "no integration is declared REQUIRED, so nothing stops a degraded build");
        assertTrue(
                cmakeLists.contains("libdbus-1-dev") && cmakeLists.contains("dbus-devel"),
                "the failure has to name the package to install, on both package managers");
    }

    @ParameterizedTest
    @EnumSource(SdlDecided.class)
    @DisplayName("the two capabilities SDL alone decides are read out of its generated header")
    void sdlDecidedCapabilitiesAreReadNotPredicted(SdlDecided decided) {
        assumeReadable();

        // The arrangement these two have, and the reason it differs from the three
        // above: there is no pkg-config prediction to confirm, because SDL decides
        // the Wayland driver with one check over five specs plus a scanner binary,
        // and a prediction narrower than what it predicts would fail builds that
        // were fine (ADR-0422). So the define comes from SDL's own answer.
        assertTrue(
                cmakeLists.contains(decided.sdlDefine()),
                "the superbuild never reads " + decided.sdlDefine() + " out of SDL_build_config.h");
        assertTrue(
                cmakeLists.contains("GOLDBERRY_PLATFORM_${_sdl_capability}"),
                "the superbuild does not derive a shim define from SDL's answer");
        assertTrue(
                shim.contains("GOLDBERRY_PLATFORM_" + decided.sdlDefine()),
                "the shim does not read GOLDBERRY_PLATFORM_" + decided.sdlDefine()
                        + ", so SDL's answer is discarded and the capability is reported wrongly");
    }

    @Test
    @DisplayName("neither of them stops the build, because neither package is installable everywhere")
    void sdlDecidedCapabilitiesDegradeRatherThanFail() {
        assumeReadable();

        // `libdecor-devel` and `xkeyboard-config` are in no repository the
        // manylinux release container has, so the honest report for that build is
        // "it cannot" rather than "install this". A REQUIRED probe here would make
        // the release container unbuildable.
        var block = cmakeLists.substring(cmakeLists.indexOf("_sdl_capability IN ITEMS"));
        assertTrue(
                block.contains("message(WARNING"),
                "a missing Wayland driver or libdecor must warn rather than stop the configure");
        assertTrue(
                block.contains("libdecor-0-dev") && block.contains("libdecor-devel"),
                "the warning has to name the package on both package managers");
        assertTrue(
                block.contains("mesa-libEGL-devel"),
                "EGL's headers are the spec whose absence has actually dropped the Wayland driver");
    }

    @Test
    @DisplayName("a build that could not read SDL's answer claims neither capability")
    void unreadableConfigClaimsNothing() {
        assumeReadable();

        // The right way round: a #ifdef that is absent reports the capability
        // absent, so the failure mode of a moved upstream header is an
        // understated library rather than an overstated one.
        var fallback = cmakeLists.substring(cmakeLists.indexOf("SDL_build_config.h was not found"));
        assertTrue(
                fallback.contains("reported as absent"),
                "the unreadable-header path must say the two capabilities are reported absent");
    }

    @Test
    @DisplayName("the capability function is exported, or Java cannot call it")
    void theCapabilityFunctionIsExported() {
        assumeReadable();

        assertTrue(
                symbols.lines().anyMatch(line -> line.strip().equals("goldberry_platform_capabilities")),
                "goldberry_platform_capabilities is not in exports/goldberry.symbols,"
                        + " so it is hidden and the binding fails at load time");
    }

    @ParameterizedTest
    @EnumSource(NativeCapability.class)
    @DisplayName("each Java bit is the C one")
    void bitsMatchTheShim(NativeCapability capability) {
        assumeReadable();

        // The layout probe checks this too, and better -- against a compiled
        // library rather than against text. It cannot run without one, and a
        // contributor with no C toolchain is the ordinary case (ADR-0016).
        var define = Pattern.compile("#define\\s+" + capability.nativeName() + "\\s+0x([0-9a-fA-F]+)u")
                .matcher(shim);
        assertTrue(define.find(), capability.nativeName() + " is not #defined in the shim");
        assertEquals(
                capability.bit(),
                Integer.parseInt(define.group(1), 16),
                capability.nativeName() + " disagrees between Java and C");
    }

    @Test
    @DisplayName("the shim's ABI version is the one the bindings were written against")
    void abiVersionsAgree() {
        assumeReadable();

        var declared =
                Pattern.compile("#define\\s+GOLDBERRY_ABI_VERSION\\s+(\\d+)u").matcher(shim);
        assertTrue(declared.find(), "the shim does not #define GOLDBERRY_ABI_VERSION");
        // A library reports the mismatch at load time, which is the real guard.
        // This one fires in the Java-only build, where the surface changes are
        // actually written -- adding an export and forgetting the bump is how two
        // artifacts come to disagree in the first place.
        assertEquals(
                GoldberryShim.SUPPORTED_ABI_VERSION,
                Integer.parseInt(declared.group(1)),
                "goldberry_shim.c and GoldberryShim.SUPPORTED_ABI_VERSION disagree");
    }

    /// A capability whose only source of truth is SDL's generated build config.
    ///
    /// Distinct from [Integration] because there is nothing to predict and nothing
    /// to cross-check: the define is *read* rather than confirmed
    /// ([ADR-0422]).
    ///
    /// @param sdlDefine  the `SDL_build_config.h` define that decides it
    /// @param capability what a library loses without it
    private enum SdlDecided {
        WAYLAND_DRIVER("SDL_VIDEO_DRIVER_WAYLAND", NativeCapability.WAYLAND),
        DECORATIONS("HAVE_LIBDECOR_H", NativeCapability.WINDOW_DECORATIONS);

        private final String sdlDefine;
        private final NativeCapability capability;

        SdlDecided(String sdlDefine, NativeCapability capability) {
            this.sdlDefine = sdlDefine;
            this.capability = capability;
        }

        String sdlDefine() {
            return sdlDefine;
        }

        NativeCapability capability() {
            return capability;
        }
    }

    /// One desktop integration, as the superbuild names it.
    ///
    /// @param modules         the pkg-config modules probed for, in SDL's own order
    /// @param define          what the probe passes to the shim
    /// @param sdlBuildConfig  the `SDL_build_config.h` define the probe predicts
    /// @param capability      what a library loses without it
    private enum Integration {
        DBUS(List.of("dbus-1", "dbus"), "GOLDBERRY_PLATFORM_DBUS", "HAVE_DBUS_DBUS_H", NativeCapability.SYSTEM_THEME),
        IBUS(List.of("ibus-1.0"), "GOLDBERRY_PLATFORM_IBUS", "HAVE_IBUS_IBUS_H", NativeCapability.INPUT_METHOD),
        UDEV(List.of("libudev"), "GOLDBERRY_PLATFORM_UDEV", "HAVE_LIBUDEV_H", NativeCapability.DEVICE_HOTPLUG);

        private final List<String> modules;
        private final String define;
        private final String sdlBuildConfig;
        private final NativeCapability capability;

        Integration(List<String> modules, String define, String sdlBuildConfig, NativeCapability capability) {
            this.modules = modules;
            this.define = define;
            this.sdlBuildConfig = sdlBuildConfig;
            this.capability = capability;
        }

        List<String> modules() {
            return modules;
        }

        String define() {
            return define;
        }

        String sdlBuildConfig() {
            return sdlBuildConfig;
        }

        NativeCapability capability() {
            return capability;
        }
    }
}
