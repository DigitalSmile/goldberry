package dev.goldberry.mic.view.draw;

import static dev.goldberry.mic.view.draw.Pixels.BLACK;
import static dev.goldberry.mic.view.draw.Pixels.assertNear;
import static dev.goldberry.mic.view.draw.Pixels.over;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.mic.analysis.LevelTracker;
import dev.goldberry.mic.analysis.Levels;
import dev.goldberry.widget.attr.Attributes;

/// A level meter's reading, drawn on a 120-pixel scale, so each 0.5 dB is a
/// pixel: RMS at −20 dB ends at 80, the hot zone starts at 108 (−6 dB), the
/// clip zone at 118 (−1 dB).
@DisplayName("a level bar")
class LevelBarTest {

    private static final LevelBar.Colours COLOURS =
            new LevelBar.Colours(0xFF00FF00, 0xFFFFFF00, 0xFFFF0000, 0xFFFFFFFF);

    /// Peak at full scale, RMS at −20 dB, held at −3 dB.
    private static final LevelTracker.Reading LOUD = new LevelTracker.Reading(new Levels(1f, 0.1f), -3);

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("fills the RMS solid, the peak faint past it, each zone in its colour, with the hold mark")
    void horizontal() {
        var bar = new LevelBar(LOUD);
        var image = Pixels.paint(120, 10, (frame, size) -> bar.paint(frame, size, COLOURS));
        assertAll(
                () -> assertNear(COLOURS.level(), image, 40, 5),
                () -> assertNear(over(COLOURS.level(), LevelBar.PEAK_ALPHA), image, 95, 5),
                () -> assertNear(over(COLOURS.hot(), LevelBar.PEAK_ALPHA), image, 110, 5),
                () -> assertNear(COLOURS.hold(), image, 114, 5),
                () -> assertNear(over(COLOURS.clip(), LevelBar.PEAK_ALPHA), image, 119, 5));
    }

    @Test
    @DisplayName("fills from the bottom when vertical")
    void vertical() {
        var bar = new LevelBar(LOUD, MeterOrientation.VERTICAL, Attributes.NONE);
        var image = Pixels.paint(10, 120, (frame, size) -> bar.paint(frame, size, COLOURS));
        assertAll(
                () -> assertNear(COLOURS.level(), image, 5, 100),
                () -> assertNear(over(COLOURS.level(), LevelBar.PEAK_ALPHA), image, 5, 25),
                () -> assertNear(COLOURS.hold(), image, 5, 6));
    }

    @Test
    @DisplayName("marks a held peak that clipped in the clip colour")
    void heldClip() {
        var bar = new LevelBar(new LevelTracker.Reading(Levels.SILENCE, -0.5));
        var image = Pixels.paint(120, 10, (frame, size) -> bar.paint(frame, size, COLOURS));
        assertAll(() -> assertNear(COLOURS.clip(), image, 118, 5), () -> assertNear(BLACK, image, 40, 5));
    }

    @Test
    @DisplayName("draws nothing for silence")
    void silence() {
        var bar = new LevelBar(new LevelTracker.Reading(Levels.SILENCE, Levels.FLOOR_DECIBELS));
        var image = Pixels.paint(120, 10, (frame, size) -> bar.paint(frame, size, COLOURS));
        for (var x = 0; x < 120; x += 7) {
            assertNear(BLACK, image, x, 5);
        }
    }

    @Test
    @DisplayName("is a level-meter to a stylesheet, and a vertical one carries the class `vertical` too")
    void styled() {
        var horizontal = new LevelBar(LOUD, MeterOrientation.HORIZONTAL, Attributes.NONE.classes("input"));
        var vertical = new LevelBar(LOUD, MeterOrientation.VERTICAL, Attributes.NONE.classes("input"));
        assertAll(
                () -> assertEquals("level-meter", horizontal.cssType()),
                () -> assertEquals(Set.of("input"), horizontal.classes()),
                () -> assertEquals(Set.of("input", "vertical"), vertical.classes()));
    }
}
