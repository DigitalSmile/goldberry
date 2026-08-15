package io.github.digitalsmile.goldberry.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The macOS first-thread diagnosis (ADR-0030).
///
/// Pure string logic, deliberately: the condition it describes cannot be
/// reproduced in a test — a JVM either started on the first thread or it did not,
/// and no test can start a second one. So the decision is separated from the
/// environment it reads, and this pins the decision.
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
        assertTrue(
                message.contains("No available video device"),
                () -> "SDL's own wording missing from: " + message);
    }

    @Test
    @DisplayName("the message stays short when the flag is irrelevant")
    void messageStaysShortOtherwise() {
        var message = Sdl3Backend.videoFailureMessage(false);

        assertEquals("SDL could not initialize its video subsystem", message);
        // A Linux user with no display must not be told to add a macOS-only flag.
        assertFalse(message.contains("-XstartOnFirstThread"));
    }
}
