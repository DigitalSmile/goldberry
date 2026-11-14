package dev.goldberry.offscreen;

import static dev.goldberry.offscreen.Scene.classed;
import static dev.goldberry.offscreen.Scene.panel;
import static dev.goldberry.offscreen.Scene.pixels;
import static dev.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.stats.FrameStage;
import dev.goldberry.widget.Widget;

/// A strip that measures: one buffer, damaged repaints, and a frame's cost.
///
/// The claim that matters most is the second one below. A partial repaint is
/// only an optimization if its picture is the full paint's picture, so every
/// frame here is checked against a [Filmstrip] taken at the same clock.
@DisplayName("a measuring strip")
class MeasuringStripTest {

    /// A transparent field with a rounded dot sliding across it and a still
    /// square beside it. Transparent on purpose: nothing opaque under the tree
    /// repaints the damage, so the strip's own clearing is what is tested.
    private static final String SLIDING = """
            @keyframes slide { from { transform: translateX(0px) } to { transform: translateX(40px) } }
            .root { width: 96px; height: 32px; flex-direction: row; }
            .dot { width: 16px; height: 16px; border-radius: 5px; background: #ff000080;
                   animation: slide 200ms linear; }
            .still { width: 16px; height: 16px; margin-left: 8px; background: #00ff00; }
            """;

    private static final String STILL = ".root { width: 32px; height: 32px; background: #0000ff }";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Widget sliding() {
        return new Scene.Panel(List.of(panel("dot"), panel("still")), classed("root"));
    }

    @Nested
    @DisplayName("what it paints")
    class Painting {

        @Test
        @DisplayName("the first frame paints the whole buffer")
        void firstFrameIsWhole() {
            try (var meter = Offscreen.of(32, 32).stylesheets(sheet(STILL)).measure(panel("root"))) {
                var frame = meter.frame();
                assertEquals(32L * 32, frame.damagedPixels());
                assertFalse(frame.isQuiet());
            }
        }

        @Test
        @DisplayName("a frame in which nothing changed paints nothing")
        void stillFrameIsQuiet() {
            try (var meter = Offscreen.of(32, 32).stylesheets(sheet(STILL)).measure(panel("root"))) {
                meter.frame();
                var before = pixels(meter.picture());

                var frame = meter.advance(16).frame();

                assertTrue(frame.isQuiet(), "damage " + frame.damage());
                assertEquals(0, frame.damagedPixels());
                assertArrayEquals(before, pixels(meter.picture()), "and the buffer is as it was");
            }
        }

        @Test
        @DisplayName("every damaged frame is the picture a full paint makes")
        void partialEqualsFull() {
            var offscreen = Offscreen.of(96, 32).stylesheets(sheet(SLIDING));
            try (var meter = offscreen.measure(sliding());
                    var strip = offscreen.strip(sliding())) {
                for (var step = 0; step < 6; step++) {
                    var measured = meter.frame();
                    var full = strip.frame();
                    assertArrayEquals(
                            pixels(full), pixels(meter.picture()), "frame " + step + ", damage " + measured.damage());
                    meter.advance(45);
                    strip.advance(45);
                }
            }
        }

        @Test
        @DisplayName("only what moved is damaged")
        void damageIsWhatMoved() {
            try (var meter = Offscreen.of(96, 32).stylesheets(sheet(SLIDING)).measure(sliding())) {
                meter.frame();
                var frame = meter.advance(50).frame();

                assertFalse(frame.isQuiet());
                assertTrue(
                        frame.damagedPixels() < 96L * 32,
                        "the dot's old and new rectangles, not the buffer: " + frame.damage());
            }
        }

        @Test
        @DisplayName("a picture is a copy the next frame does not touch")
        void pictureIsACopy() {
            try (var meter = Offscreen.of(96, 32).stylesheets(sheet(SLIDING)).measure(sliding())) {
                meter.frame();
                var first = meter.picture();
                var kept = pixels(first);

                meter.advance(100).frame();

                assertArrayEquals(kept, pixels(first));
                assertFalse(Arrays.equals(kept, pixels(meter.picture())), "the buffer itself moved on");
            }
        }
    }

    @Nested
    @DisplayName("what it reports")
    class Reporting {

        @Test
        @DisplayName("each frame's stages, numbered as a window numbers them")
        void stagesPerFrame() {
            try (var meter = Offscreen.of(96, 32).stylesheets(sheet(SLIDING)).measure(sliding())) {
                var first = meter.frame();
                var second = meter.advance(16).frame();

                assertEquals(1, first.timings().number());
                assertEquals(2, second.timings().number());
                assertEquals(2, meter.frames());
                assertTrue(first.timings().paintNanos() > 0, "the paint was timed");
                assertTrue(
                        first.timings().paintNanos()
                                >= first.timings().buildNanos()
                                        + first.timings().styleNanos()
                                        + first.timings().layoutNanos(),
                        "and the stages are inside it");
                assertFalse(first.timings().composited(), "nothing is presented");
                assertEquals(0, first.timings().uploadBytes());
            }
        }

        @Test
        @DisplayName("percentiles over the frames, as a window's statistics read")
        void percentiles() {
            try (var meter = Offscreen.of(32, 32).stylesheets(sheet(STILL)).measure(panel("root"))) {
                for (var i = 0; i < 10; i++) {
                    meter.frame();
                    meter.advance(16);
                }
                var stats = meter.stats();
                assertEquals(10, stats.count());
                assertTrue(stats.percentile(FrameStage.PAINT, 99) >= stats.percentile(FrameStage.PAINT, 50));
                assertTrue(stats.percentile(FrameStage.PAINT, 100) > 0);
            }
        }
    }

    @Nested
    @DisplayName("its lifetime")
    class Lifetime {

        @Test
        @DisplayName("is closed once, and refuses a frame afterwards")
        void closes() {
            var meter = Offscreen.of(32, 32).stylesheets(sheet(STILL)).measure(panel("root"));
            var picture = meter.frame();
            meter.close();
            meter.close();

            assertThrows(IllegalStateException.class, meter::frame);
            assertThrows(IllegalStateException.class, meter::picture);
            assertEquals(32L * 32, picture.damagedPixels(), "what it reported stays");
            assertTrue(meter.toString().contains("closed"), meter.toString());
        }

        @Test
        @DisplayName("refuses a clock that runs backwards")
        void forwardsOnly() {
            try (var meter = Offscreen.of(32, 32).stylesheets(sheet(STILL)).measure(panel("root"))) {
                assertThrows(IllegalArgumentException.class, () -> meter.advance(-1));
                assertEquals(0, meter.nowMillis());
            }
        }
    }
}
