package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Threaded painting must produce the same frame as synchronous painting.
///
/// This is the test that makes ADR-0042 safe to have made. Blend2D's workers
/// split a frame into horizontal bands, and a band boundary landing in the
/// middle of an antialiased edge — or a frame read before the bands finished —
/// would show up as a seam or as a partly-drawn frame. Neither would fail
/// anything else in the suite: every other rendering test paints synchronously.
class ThreadedPaintTest {

    private static final String PROSE =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. Shaping happens once;"
                    + " every wrap after that is arithmetic over the glyphs it produced.";

    private static final int WIDTH = 480;
    private static final int HEIGHT = 320;

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 8})
    @DisplayName("a threaded frame is pixel-identical to a synchronous one")
    void threadedFrameMatchesSynchronous(int threads) {
        var scene = scene();

        var expected = paint(0, scene);
        var actual = paint(threads, scene);

        // Every pixel, not a sample: a band seam is one row wrong out of three
        // hundred, and a spot check is exactly the test that would miss it.
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var index = y * WIDTH + x;
                if (expected[index] != actual[index]) {
                    var fx = x;
                    var fy = y;
                    assertEquals(expected[index], actual[index],
                            () -> "with " + threads + " worker(s), pixel (" + fx + "," + fy
                                    + ") differs from the synchronous frame");
                }
            }
        }
    }

    @Test
    @DisplayName("the frame reports the workers it actually got")
    void frameReportsItsWorkerCount() {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f, 0);
        try {
            assertEquals(0, target.frame().threadCount());
        } finally {
            target.end();
        }

        var threaded = TestFrames.of(WIDTH, HEIGHT, 1.0f, 3);
        try {
            // Blend2D may refuse the workers, in which case zero is the honest
            // answer and the frame still paints. What it may never do is report
            // a count nobody asked for.
            assertTrue(threaded.frame().threadCount() == 3 || threaded.frame().threadCount() == 0,
                    () -> "unexpected worker count " + threaded.frame().threadCount());
        } finally {
            threaded.end();
        }
    }

    @Test
    @DisplayName("a threaded frame really did paint something")
    void threadedFrameIsNotBlank() {
        // Guards the way this test could pass for the wrong reason: if both
        // frames came back empty, every pixel would match and prove nothing.
        var painted = paint(4, scene());
        var blank = new int[WIDTH * HEIGHT];

        assertNotEquals(0, painted[WIDTH * HEIGHT / 2], "the middle of the frame is untouched");
        assertNotEquals(java.util.Arrays.hashCode(blank), java.util.Arrays.hashCode(painted));
    }

    /// Paints the scene and returns the finished pixels, read after `end()`.
    private int[] paint(int threads, Box scene) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f, threads);
        try {
            BoxPainter.paint(target.frame(), scene);
        } finally {
            // Before a single pixel is read. With workers attached this is where
            // the bands are waited for, so reading first is how a green test
            // would hide an asynchronous frame that was never finished.
            target.end();
        }

        var pixels = new int[WIDTH * HEIGHT];
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = target.pixel(x, y);
            }
        }
        return pixels;
    }

    private Box scene() {
        var paragraph = Paragraph.of(font, PROSE);
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .background(0xFF2E3440)
                .children(
                        Box.of()
                                .background(0xFF88C0D0)
                                .size(StyleLength.UNDEFINED, StyleLength.points(32))
                                .alignItems(Align.CENTER)
                                .padding(StyleLength.points(8))
                                .children(Box.text(Paragraph.of(font, "Goldberry"), 0xFF2E3440)),
                        Box.of()
                                .grow(1)
                                .direction(FlexDirection.ROW)
                                .padding(StyleLength.points(12))
                                .gap(StyleLength.points(12))
                                .children(
                                        // Half-transparent, so the comparison
                                        // covers blending and not only opaque
                                        // fills -- a band boundary in the middle
                                        // of a composite is the interesting case.
                                        Box.filled(0x803B4252).size(
                                                StyleLength.percent(30), StyleLength.UNDEFINED),
                                        Box.of()
                                                .grow(1)
                                                .direction(FlexDirection.COLUMN)
                                                .background(0xFF4C566A)
                                                .padding(StyleLength.points(10))
                                                .children(Box.text(paragraph, 0xFFECEFF4))));
    }
}
