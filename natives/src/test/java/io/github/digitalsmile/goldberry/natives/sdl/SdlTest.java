package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SDL3 through the real `libgoldberry`.
///
/// Nothing here touches video. These tests run on CI runners with no display, and
/// `SDL_INIT_VIDEO` would fail there for reasons that have nothing to do with the
/// bindings — the point is to prove the symbols are reachable and the calling
/// conventions are right, which the events subsystem does without a screen.
///
/// SDL is process-global state, so every test that initializes it tears it down
/// again.
class SdlTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("reports the SDL that was statically linked")
    void reportsLinkedVersion() {
        var version = Sdl.get().version();

        // Static linking makes this a build fact: there is no system SDL that
        // could disagree, so a surprise here means the pinned ref in
        // gradle/libs.versions.toml is not what was built.
        assertTrue(
                version.isAtLeast(Sdl.MINIMUM_VERSION),
                () -> "linked SDL is " + version + ", below the " + Sdl.MINIMUM_VERSION
                        + " these bindings were written against");
        assertEquals(3, version.major(), () -> "not SDL3: " + version);
    }

    /// `SDL_GetModState` returns a `Uint16`, which is the whole reason this test
    /// is worth having: a 16-bit return bound as `JAVA_INT` reads two bytes of
    /// whatever else is in the return register, and on a machine with no keys
    /// held the right answer and the wrong answer are both usually zero. Pressing
    /// a key is not something a headless test can do, so what is checked is that
    /// the call **crosses at all** — the symbol is on the export list, the
    /// descriptor matches, and nothing above the low 16 bits leaks through
    /// ([ADR-0089]).
    @Test
    @DisplayName("the modifier state crosses, and carries no bits above the mask")
    void readsModifierState() {
        var state = Sdl.get().modifierState();

        assertEquals(0, state & ~0xFFFF,
                () -> "SDL_Keymod is a Uint16 and this came back as 0x"
                        + Integer.toHexString(state) + "; the descriptor is wrong");
        assertTrue(state >= 0, "widened unsigned, so never negative");
    }

    @Test
    @DisplayName("the revision string comes back as a string, not a dangling pointer")
    void readsRevision() {
        // const char* returns are the easiest thing to get wrong in a hand-written
        // binding, and the failure mode is a segfault rather than a wrong answer.
        assertNotNull(Sdl.get().revision());
    }

    @Test
    @DisplayName("initializes and shuts down a subsystem that needs no display")
    void initializesEvents() {
        var sdl = Sdl.get();

        sdl.initialize(EnumSet.of(SdlSubsystem.EVENTS));

        assertTrue(
                sdl.wasInit().contains(SdlSubsystem.EVENTS),
                () -> "SDL_WasInit reported " + sdl.wasInit());
    }

    @Test
    @DisplayName("quitting a subsystem is visible to SDL_WasInit")
    void quitsSubsystem() {
        var sdl = Sdl.get();
        sdl.initialize(EnumSet.of(SdlSubsystem.EVENTS));

        sdl.quitSubsystems(EnumSet.of(SdlSubsystem.EVENTS));

        assertEquals(Set.of(), sdl.wasInit());
    }

    @Test
    @DisplayName("nothing is initialized before SDL_Init")
    void nothingInitializedInitially() {
        // @AfterEach quits SDL, so this holds however the tests are ordered.
        assertEquals(Set.of(), Sdl.get().wasInit());
    }

    @Test
    @DisplayName("subsystems can be added to a running SDL")
    void addsSubsystems() {
        var sdl = Sdl.get();
        sdl.initialize(EnumSet.of(SdlSubsystem.EVENTS));

        sdl.initializeSubsystems(EnumSet.of(SdlSubsystem.SENSOR));

        assertTrue(sdl.wasInit().containsAll(EnumSet.of(SdlSubsystem.EVENTS, SdlSubsystem.SENSOR)));
    }

    @Test
    @DisplayName("the error string is readable and clearable")
    void errorRoundTrips() {
        var sdl = Sdl.get();

        sdl.clearError();

        // Deliberately not asserting a non-empty error: producing one reliably
        // means finding a call that fails identically on six targets, and the
        // binding under test here is the char* read, not SDL's error policy.
        assertEquals("", sdl.lastError());
    }

    @Test
    @DisplayName("initializing twice is not an error")
    void doubleInitIsFine() {
        var sdl = Sdl.get();

        sdl.initialize(EnumSet.of(SdlSubsystem.EVENTS));
        sdl.initialize(EnumSet.of(SdlSubsystem.EVENTS));

        assertTrue(sdl.wasInit().contains(SdlSubsystem.EVENTS));
    }
}
