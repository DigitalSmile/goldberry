package io.github.digitalsmile.goldberry.natives.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.NativePlatform;

/// What the library on this machine actually reports — `docs/gaps.md` G32.
///
/// The interesting assertion is not "it has everything": a build made on purpose
/// without the D-Bus headers is a legitimate library and this test must pass
/// against it. What can be asserted is that the answer is *well formed*, that it
/// is stable, and — on the two platforms where these are system frameworks rather
/// than optional headers — that it is complete.
@DisplayName("the loaded library's capabilities")
class NativeCapabilitiesTest {

    @Test
    @DisplayName("the answer is asked once and does not change")
    void theAnswerIsStable() {
        NativeLibraryRequirement.enforce();

        // A build-time constant on the other side. If this ever stopped holding,
        // something would be probing the running session instead of reporting the
        // library, which is the distinction the whole capability is about.
        assertEquals(NativeCapabilities.get(), NativeCapabilities.get());
        assertEquals(
                NativeCapabilities.get(),
                NativeCapabilities.read(NativeLibrary.get().lookup()));
    }

    @Test
    @DisplayName("macOS and Windows have all of them, because none is an optional header there")
    void theFrameworkPlatformsHaveEverything() {
        NativeLibraryRequirement.enforce();

        var os = NativeLibrary.get().platform().os();
        // Linux is deliberately absent from this check: there, each of these is a
        // third-party -dev package that may or may not have been installed on the
        // build machine, and a library without them is exactly the thing this
        // whole mechanism exists to describe rather than to forbid.
        // Aborted rather than returned. A `return` here reported a green tick for
        // a test that had asserted nothing, on the one platform where this suite
        // usually runs (the 2026-09-18 review, §6).
        assumeFalse(
                os == NativePlatform.OperatingSystem.LINUX,
                "on Linux each capability is a -dev package that may not be installed");
        assertEquals(
                EnumSet.allOf(NativeCapability.class),
                EnumSet.copyOf(NativeCapabilities.get()),
                "on " + os + " every capability is a system framework SDL is always compiled against");
    }

    @Test
    @DisplayName("a Linux build says which of the three it was built with")
    void linuxReportsWhatItWasBuiltWith() {
        NativeLibraryRequirement.enforce();

        if (NativeLibrary.get().platform().os() != NativePlatform.OperatingSystem.LINUX) {
            return;
        }
        var capabilities = NativeCapabilities.get();
        // The D-Bus trio moves together: one #define gates SDL_system_theme.c, the
        // portal dialog and the screensaver inhibit, so a library with one of them
        // and not the others would mean the superbuild's probe and SDL's have
        // diverged.
        var theme = capabilities.contains(NativeCapability.SYSTEM_THEME);
        assertEquals(
                theme,
                capabilities.contains(NativeCapability.FILE_DIALOG),
                "the portal file dialog and the system theme are one D-Bus build flag");
        assertEquals(
                theme,
                capabilities.contains(NativeCapability.SCREENSAVER_INHIBIT),
                "the screensaver inhibit and the system theme are one D-Bus build flag");
    }
}
