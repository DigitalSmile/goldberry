package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;

/// `SDL_SystemTheme` — `docs/gaps.md` G26, ADR-0322.
///
/// The ordinals themselves are checked against the compiled SDL by the layout
/// probe, which is where a wrong one would actually be caught. What is left for
/// this is the Java side of the enum: the two spellings it exposes, and what it
/// does with a value it has never seen.
class SdlSystemThemeTest {

    /// The literals are SDL's, from `SDL_SystemTheme` in `SDL3/SDL_video.h`.
    @ParameterizedTest
    @CsvSource({
        "UNKNOWN, 0",
        "LIGHT,   1",
        "DARK,    2",
    })
    @DisplayName("the ordinals are SDL's")
    void valuesMatchSdl(SdlSystemTheme theme, int expected) {
        assertEquals(expected, theme.value());
    }

    @ParameterizedTest
    @EnumSource(SdlSystemTheme.class)
    @DisplayName("every theme survives a round trip through its value")
    void roundTrips(SdlSystemTheme theme) {
        assertSame(theme, SdlSystemTheme.of(theme.value()));
    }

    /// The tolerant rule, and it is a choice rather than laziness: a future SDL
    /// that learns a third appearance should leave an application in its own
    /// default, because "the desktop says something I do not understand" and "the
    /// desktop does not say" are the same answer to everyone above this line.
    @ParameterizedTest
    @ValueSource(ints = {3, 99, -1, Integer.MIN_VALUE})
    @DisplayName("a value this enum predates is unknown rather than a failure")
    void unknownValuesAreUnknown(int value) {
        assertSame(SdlSystemTheme.UNKNOWN, SdlSystemTheme.of(value));
    }

    @ParameterizedTest
    @EnumSource(SdlSystemTheme.class)
    @DisplayName("the probe's name is the C one")
    void nativeNames(SdlSystemTheme theme) {
        assertEquals("SDL_SYSTEM_THEME_" + theme.name(), theme.nativeName());
    }

    /// Against the real library, where there is one: the call must **answer**
    /// rather than throw, whatever this machine's desktop happens to be set to —
    /// including a headless CI box, where the answer is `UNKNOWN` and that is not a
    /// failure.
    @Test
    @DisplayName("the real library answers, whatever the answer is")
    void theLibraryAnswers() {
        NativeLibraryRequirement.enforce();

        assertSame(
                SdlSystemTheme.of(SdlVideo.get().systemTheme().value()),
                SdlVideo.get().systemTheme());
    }
}
