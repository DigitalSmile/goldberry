package dev.goldberry.mic.view.draw;

import static dev.goldberry.mic.view.draw.Pixels.BLACK;
import static dev.goldberry.mic.view.draw.Pixels.assertNear;
import static dev.goldberry.mic.view.draw.Pixels.over;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.widget.attr.Attributes;

/// Three bands on 90 × 60 pixels: full scale, half way up the 90 dB scale,
/// and the floor.
@DisplayName("a spectrum plot")
class SpectrumPlotTest {

    private static final int COLOUR = 0xFF00FF00;

    private static final List<Double> BANDS = List.of(0.0, -45.0, -90.0);

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("draws a bar per band from the bottom, as tall as the band is loud")
    void bars() {
        var plot = new SpectrumPlot(BANDS);
        var image = Pixels.paint(90, 60, (frame, size) -> plot.paint(frame, size, COLOUR));
        assertAll(
                () -> assertNear(COLOUR, image, 15, 2),
                () -> assertNear(COLOUR, image, 45, 45),
                () -> assertNear(BLACK, image, 45, 20),
                () -> assertNear(BLACK, image, 75, 58),
                () -> assertNear(BLACK, image, 30, 45));
    }

    @Test
    @DisplayName("draws a line through the bands with the area beneath it shaded")
    void line() {
        var plot = new SpectrumPlot(BANDS, SpectrumShape.LINE, Attributes.NONE);
        var image = Pixels.paint(90, 60, (frame, size) -> plot.paint(frame, size, COLOUR));
        assertAll(
                () -> assertNear(over(COLOUR, SpectrumPlot.FILL_ALPHA), image, 15, 40),
                () -> assertNear(BLACK, image, 75, 20));
    }

    @Test
    @DisplayName("draws nothing with no bands, and is a spectrum-view to a stylesheet")
    void empty() {
        var plot = new SpectrumPlot(List.of());
        var image = Pixels.paint(90, 60, (frame, size) -> plot.paint(frame, size, COLOUR));
        assertNear(BLACK, image, 45, 30);
        assertEquals("spectrum-view", plot.cssType());
    }

    @Test
    @DisplayName("holds its bands as values, so two plots of the same reading are equal")
    void values() {
        var levels = SpectrumPlot.levels(new double[] {-3, -6});
        assertEquals(List.of(-3.0, -6.0), levels);
        assertEquals(new SpectrumPlot(levels), new SpectrumPlot(List.of(-3.0, -6.0)));
    }
}
