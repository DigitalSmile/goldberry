package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;

/// `SDL_SystemTheme` — `docs/gaps.md` G26, ADR-0322.
///
/// The ordinals themselves are checked against the compiled SDL by the layout
/// probe — every `SdlSystemTheme` is a row of `NativeConstants.registry()` — so
/// they are not restated here. What is left for this is the Java side of the
/// enum: the round trip, and what it does with a value it has never seen.
class SdlSystemThemeTest {

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

    /// Against the real library, where there is one: the call must **answer**
    /// rather than throw, whatever this machine's desktop happens to be set to —
    /// including a headless CI box, where the answer is `UNKNOWN` and that is not a
    /// failure.
    ///
    /// Nothing else in the suite crosses into `SDL_GetSystemTheme`, so this is the
    /// only place a wrong descriptor or a missing export shows up; the value is the
    /// desktop's and is not asserted.
    @Test
    @DisplayName("the real library answers, whatever the answer is")
    void theLibraryAnswers() {
        NativeLibraryRequirement.enforce();

        assertNotNull(SdlVideo.get().systemTheme());
    }
}
