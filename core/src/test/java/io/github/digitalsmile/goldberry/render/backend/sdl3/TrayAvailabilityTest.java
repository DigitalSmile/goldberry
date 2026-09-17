package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Tests for [TrayAvailability].
///
/// The one case that matters is the one that aborted a process; the rest pin
/// down that nothing else is refused, because a tray that vanished on Linux
/// would be a regression this check must not be able to cause.
@DisplayName("TrayAvailability")
class TrayAvailabilityTest {

    @ParameterizedTest(name = "{0} under {1}")
    @CsvSource({"Mac OS X, dummy", "Darwin, dummy"})
    @DisplayName("refuses a tray on macOS under the dummy driver, and says why")
    void macOsUnderDummyIsRefused(String osName, String driver) {
        var reason = TrayAvailability.absenceReason(osName, driver);
        assertAll(
                () -> assertTrue(reason.isPresent()),
                () -> assertTrue(reason.orElseThrow().contains("Cocoa"), reason.toString()),
                () -> assertTrue(reason.orElseThrow().contains(TrayAvailability.DUMMY_DRIVER), reason.toString()));
    }

    @ParameterizedTest(name = "{0} under {1}")
    @CsvSource({
        "Mac OS X, cocoa",
        "Linux, dummy",
        "Linux, x11",
        "Linux, wayland",
        "Windows 11, dummy",
        "Windows 11, windows"
    })
    @DisplayName("lets every other combination ask SDL")
    void everythingElseIsAllowed(String osName, String driver) {
        assertEquals(Optional.empty(), TrayAvailability.absenceReason(osName, driver));
    }

    @Test
    @DisplayName("an unknown system is not refused")
    void unknownSystemIsAllowed() {
        assertAll(
                () -> assertEquals(Optional.empty(), TrayAvailability.absenceReason(null, "dummy")),
                () -> assertEquals(Optional.empty(), TrayAvailability.absenceReason("", "dummy")),
                () -> assertEquals(Optional.empty(), TrayAvailability.absenceReason("Mac OS X", null)));
    }
}
