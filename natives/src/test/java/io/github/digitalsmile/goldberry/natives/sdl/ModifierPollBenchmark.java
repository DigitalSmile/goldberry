package io.github.digitalsmile.goldberry.natives.sdl;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;

/// What the per-event modifier poll costs.
///
/// ADR-0089 has every pointer event carry the modifier bitmask, and SDL's mouse
/// events have no `mod` field where its keyboard events do — so the backend
/// polls `SDL_GetModState` inside the pump that produced the event, which is the
/// closest to "when it happened" that layer can get. The TODO entry that asked
/// for this said: *"On a 120 Hz trackpad that is a few thousand calls a second
/// into a statically linked function that reads a global. Not measured — named
/// here so it can be if a profile ever points at it."*
///
/// This is the measurement. It is the same shape as
/// [io.github.digitalsmile.goldberry.natives.DowncallBenchmark], because the
/// question is the same one: what does one crossing cost, and how much of a frame
/// is a few thousand of them.
///
/// **Nothing here touches video.** `SDL_GetModState` reads a global that the
/// events subsystem keeps, so it answers on a CI runner with no display exactly
/// as [SdlTest] proves the call itself does.
///
/// **Tagged `benchmark`, so `check` never runs it.** Run with
/// `./gradlew :natives:benchmark`.
@Tag("benchmark")
class ModifierPollBenchmark {

    private static final long WARMUP = 2_000_000L;
    private static final long RUNS = 20_000_000L;

    /// The rate the entry named: a 120 Hz trackpad delivering motion events, plus
    /// the presses and wheel turns that go with them. Deliberately generous — the
    /// point is an upper bound on a cost, and a bound worth stating is one nobody
    /// can call optimistic.
    private static final int EVENTS_PER_SECOND = 4_000;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterAll
    static void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("SDL_GetModState, per call and per second of dragging")
    void perCall() {
        poll(WARMUP);

        var started = System.nanoTime();
        var sink = poll(RUNS);
        var elapsed = System.nanoTime() - started;

        if (sink == Integer.MIN_VALUE) {
            // Not an assertion about speed -- an assertion that the loop ran, so
            // an optimiser that deleted it cannot be reported as free.
            throw new AssertionError("the benchmark loop did not call the library");
        }

        var perCall = (double) elapsed / RUNS;
        System.out.printf(
                Locale.ROOT,
                "SDL_GetModState  %.2f ns/call   %.1f us/s at %d events/s   %.4f%% of one core%n",
                perCall,
                perCall * EVENTS_PER_SECOND / 1_000,
                EVENTS_PER_SECOND,
                perCall * EVENTS_PER_SECOND / 10_000_000);
    }

    /// The bitmask is accumulated rather than discarded, so the call cannot be
    /// optimised away as dead.
    private static int poll(long iterations) {
        var sdl = Sdl.get();
        var sink = 0;
        for (var i = 0L; i < iterations; i++) {
            sink += sdl.modifierState();
        }
        return sink;
    }
}
