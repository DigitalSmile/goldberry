package dev.goldberry.mic.view.draw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a decibel scale")
class DecibelScaleTest {

    @Test
    @DisplayName("lays decibels linearly from the floor to the ceiling, and clamps the rest")
    void fraction() {
        var scale = new DecibelScale(-60, 0);
        assertAll(
                () -> assertEquals(0, scale.fraction(-60)),
                () -> assertEquals(0.5, scale.fraction(-30), 1e-12),
                () -> assertEquals(1, scale.fraction(0)),
                () -> assertEquals(0, scale.fraction(-120), "below the floor"),
                () -> assertEquals(1, scale.fraction(6), "past the ceiling"),
                () -> assertEquals(0, scale.fraction(Double.NaN)));
    }

    @Test
    @DisplayName("refuses a ceiling at or below its floor, and an endless end")
    void refuses() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new DecibelScale(0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new DecibelScale(0, -10)),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new DecibelScale(Double.NEGATIVE_INFINITY, 0)));
    }

    @Test
    @DisplayName("names its orientations and shapes as markup writes them")
    void names() {
        assertAll(
                () -> assertEquals(MeterOrientation.HORIZONTAL, MeterOrientation.named(null)),
                () -> assertEquals(MeterOrientation.VERTICAL, MeterOrientation.named("Vertical")),
                () -> assertThrows(IllegalArgumentException.class, () -> MeterOrientation.named("diagonal")),
                () -> assertEquals(SpectrumShape.BARS, SpectrumShape.named(null)),
                () -> assertEquals(SpectrumShape.LINE, SpectrumShape.named("line")),
                () -> assertThrows(IllegalArgumentException.class, () -> SpectrumShape.named("dots")));
    }
}
