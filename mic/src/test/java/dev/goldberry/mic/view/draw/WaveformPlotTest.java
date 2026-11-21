package dev.goldberry.mic.view.draw;

import static dev.goldberry.mic.view.draw.Pixels.BLACK;
import static dev.goldberry.mic.view.draw.Pixels.assertNear;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.mic.analysis.Envelope;
import dev.goldberry.widget.attr.Attributes;

/// An outline of two buckets on 100 × 40 pixels: the first from −0.5 to 0.5,
/// so rows 10 to 30, the second silent.
@DisplayName("a waveform plot")
class WaveformPlotTest {

    private static final WaveformPlot.Colours COLOURS = new WaveformPlot.Colours(0xFF00FF00, 0xFF0000FF);

    private static final Envelope HALF = Envelope.of(new float[] {-0.5f, 0.5f, 0f, 0f}, 1, 2);

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("draws each bucket from its lowest sample to its highest, the played part in the wave colour")
    void played() {
        var plot = new WaveformPlot(HALF, 0.5, false, Attributes.NONE);
        var image = Pixels.paint(100, 40, (frame, size) -> plot.paint(frame, size, COLOURS));
        assertAll(
                () -> assertNear(COLOURS.wave(), image, 25, 12),
                () -> assertNear(COLOURS.wave(), image, 25, 28),
                () -> assertNear(BLACK, image, 25, 6),
                () -> assertNear(BLACK, image, 75, 10),
                () -> assertNotEquals(BLACK, image.argb(75, 19) | image.argb(75, 20), "silence is still a line"),
                () -> assertNear(BLACK, image, 50, 20, "two buckets in a hundred pixels are two bars"));
    }

    @Test
    @DisplayName("draws an outline with a bucket a column or more as one continuous shape")
    void continuous() {
        var samples = new float[400];
        for (var frame = 0; frame < samples.length; frame++) {
            samples[frame] = frame % 2 == 0 ? 0.5f : -0.5f;
        }
        var plot = new WaveformPlot(Envelope.of(samples, 1, 200));
        var image = Pixels.paint(100, 40, (frame, size) -> plot.paint(frame, size, COLOURS));
        for (var x = 0; x < 100; x += 9) {
            assertNear(COLOURS.wave(), image, x, 20, "no gaps");
        }
    }

    @Test
    @DisplayName("draws what is not played yet in the ahead colour")
    void ahead() {
        var plot = new WaveformPlot(HALF, 0, false, Attributes.NONE);
        var image = Pixels.paint(100, 40, (frame, size) -> plot.paint(frame, size, COLOURS));
        assertNear(COLOURS.ahead(), image, 25, 20);
    }

    @Test
    @DisplayName("fills the height with the loudest bucket when normalized")
    void normalized() {
        var plot = new WaveformPlot(HALF, 1, true, Attributes.NONE);
        var image = Pixels.paint(100, 40, (frame, size) -> plot.paint(frame, size, COLOURS));
        assertNear(COLOURS.wave(), image, 25, 2);
    }

    @Test
    @DisplayName("does not blow silence up to the whole height")
    void quietIsNotNormalized() {
        var quiet = Envelope.of(new float[] {-1e-4f, 1e-4f}, 1, 1);
        var plot = new WaveformPlot(quiet, 1, true, Attributes.NONE);
        var image = Pixels.paint(100, 40, (frame, size) -> plot.paint(frame, size, COLOURS));
        assertNear(BLACK, image, 50, 5);
    }

    @Test
    @DisplayName("is a waveform-view to a stylesheet, and refuses a played share outside 0 to 1")
    void styledAndRefuses() {
        assertAll(
                () -> assertEquals("waveform-view", new WaveformPlot(HALF).cssType()),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> new WaveformPlot(HALF, 1.5, false, Attributes.NONE)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new WaveformPlot(HALF, Double.NaN, false, Attributes.NONE)));
    }
}
