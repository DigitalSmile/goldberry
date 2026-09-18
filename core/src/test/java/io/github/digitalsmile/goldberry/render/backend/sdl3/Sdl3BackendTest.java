package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The macOS first-thread diagnosis (ADR-0030), and the pump's arithmetic.
///
/// Pure logic, deliberately: the condition the first one describes cannot be
/// reproduced in a test — a JVM either started on the first thread or it did not,
/// and no test can start a second one — and the second is a decision about how
/// long to block, which a test that actually blocked could only measure with a
/// stopwatch. So both are separated from what they read, and this pins them.
class Sdl3BackendTest {

    @ParameterizedTest
    @CsvSource({
        "Mac OS X, , true",
        "Mac OS X, 0, true",
        "Darwin,   , true",
        "Mac OS X, 1, false",
        "Linux,    , false",
        "Linux,    1, false",
        "Windows 11, , false",
    })
    @DisplayName("the flag is only worth mentioning on macOS, and only when unset")
    void flagMentionedOnlyWhenItApplies(String osName, String env, boolean expected) {
        assertEquals(expected, Sdl3Backend.firstThreadFlagLikelyMissing(osName, env));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "SunOS"})
    @DisplayName("an unknown or absent os.name is never diagnosed as the macOS case")
    void unknownPlatformIsNotDiagnosed(String osName) {
        // Guessing "you forgot -XstartOnFirstThread" at someone on an OS where the
        // flag does not exist would send them somewhere there is nothing to find.
        assertFalse(Sdl3Backend.firstThreadFlagLikelyMissing(osName, null));
    }

    @Test
    @DisplayName("the message names the flag when the flag is the likely cause")
    void messageNamesTheFlag() {
        var message = Sdl3Backend.videoFailureMessage(true);

        assertTrue(message.contains("-XstartOnFirstThread"), () -> "flag missing from: " + message);
        // The string SDL itself prints, so that searching for the error people
        // actually see leads to the explanation rather than away from it.
        assertTrue(message.contains("No available video device"), () -> "SDL's own wording missing from: " + message);
    }

    @Test
    @DisplayName("the message stays short when the flag is irrelevant")
    void messageStaysShortOtherwise() {
        var message = Sdl3Backend.videoFailureMessage(false);

        assertEquals("SDL could not initialize its video subsystem", message);
        // A Linux user with no display must not be told to add a macOS-only flag.
        assertFalse(message.contains("-XstartOnFirstThread"));
    }

    /// How long the pump asks SDL to block for.
    ///
    /// [FramePacer] decides the wait in nanoseconds and `SDL_WaitEventTimeout`
    /// takes whole milliseconds, and the whole of the bug lived in the crossing.
    @Nested
    @DisplayName("the wait, in SDL's milliseconds")
    class TheWait {

        /// **The busy loop.** A paced pump holds a frame back and shortens its
        /// wait to what is left of the interval; the last millisecond of every
        /// such interval truncated to `0`, which is `SDL_PollEvent`, which
        /// returns at once with nothing. The pump delivered nothing, the event
        /// loop came round again, and the two spun against each other for up to
        /// a millisecond per frame — on every machine, since the display's own
        /// rate is adopted without anyone asking for it.
        @Test
        @DisplayName("a wait under a millisecond still waits, rather than polling")
        void aSubMillisecondWaitIsNotAPoll() {
            assertEquals(1, Sdl3Backend.waitMillis(Duration.ofNanos(1)));
            assertEquals(1, Sdl3Backend.waitMillis(Duration.ofNanos(999_999)));
            // The shape the pacer actually produces: what is left of a 16.6 ms
            // interval, a hair before the frame comes due.
            assertEquals(1, Sdl3Backend.waitMillis(Duration.ofNanos(500_000)));
        }

        @Test
        @DisplayName("only a zero wait polls, because only a zero timeout asked to")
        void onlyZeroPolls() {
            assertEquals(0, Sdl3Backend.waitMillis(Duration.ZERO));
            // A negative wait is not reachable from pumpEvents, which refuses a
            // negative timeout -- but rounding one up to a millisecond of
            // blocking would be the wrong way to be wrong.
            assertEquals(0, Sdl3Backend.waitMillis(Duration.ofMillis(-5)));
        }

        @Test
        @DisplayName("a wait that is not whole milliseconds rounds up, not down")
        void theRoundingGoesUp() {
            // Rounding down would end the wait before the frame was due and buy
            // another pump for the remainder -- which is the busy loop again,
            // one iteration longer each time.
            assertEquals(17, Sdl3Backend.waitMillis(Duration.ofNanos(16_666_666)));
            assertEquals(2, Sdl3Backend.waitMillis(Duration.ofNanos(1_000_001)));
            // A whole millisecond is already whole and gains nothing.
            assertEquals(1, Sdl3Backend.waitMillis(Duration.ofMillis(1)));
            assertEquals(1000, Sdl3Backend.waitMillis(Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("a wait longer than SDL can hold is capped, not wrapped")
        void anEnormousWaitIsCapped() {
            // The SPI takes a Duration and puts no ceiling on it. Truncating to
            // int would make a 25-day timeout a negative one, and SDL reads a
            // negative timeout as "wait forever".
            assertEquals(Integer.MAX_VALUE, Sdl3Backend.waitMillis(Duration.ofDays(365)));
            assertEquals(Integer.MAX_VALUE, Sdl3Backend.waitMillis(Duration.ofSeconds(Long.MAX_VALUE / 1000)));
        }
    }
}
