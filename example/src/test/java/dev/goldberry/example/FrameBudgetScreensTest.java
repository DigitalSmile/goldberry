package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.example.ui.Screen;

/// The benchmark's screens are the gallery's, resolved under `check`; see
/// [FrameBudgetScreens].
class FrameBudgetScreensTest {

    @ParameterizedTest(name = "\"{0}\" is a gallery screen")
    @ValueSource(strings = {FrameBudgetScreens.WALL, FrameBudgetScreens.DOCUMENT, FrameBudgetScreens.SHEET})
    void theMeasuredScreensExist(String screen) {
        assertTrue(
                Screen.GALLERY.contains(screen),
                "FrameBudgetBenchmark measures \"" + screen + "\", and the gallery is " + Screen.GALLERY);
    }
}
