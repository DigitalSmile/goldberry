package io.github.digitalsmile.goldberry.widgets.data.sparkline;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// §11's `sparkline` — the first chart, and the one `statistic` has been waiting
/// for since M2.
///
/// A sparkline has no axes and no labels, so almost everything true of it is true
/// about *pixels*: where the line sits for a given series is the whole contract.
/// These drive the painter directly against a size, because a test that had to
/// build a tree to reach the drawing would be testing the tree.
class SparklineTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final int INK = 0xFF88C0D0;

    /// Paints `sparkline` into a `width`×`height` frame and returns the target,
    /// so a test can read pixels out of it.
    private static TestFrames.Target painted(Sparkline sparkline, int width, int height) {
        var target = TestFrames.of(width, height, 1.0f);
        try {
            target.frame().fill(0xFF000000);
            sparkline.paint(target.frame(), new LogicalSize(width, height), INK);
        } finally {
            target.end();
        }
        return target;
    }

    /// The topmost row holding any ink, or -1.
    private static int topmostInk(TestFrames.Target target, int width, int height) {
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (target.pixel(x, y) != 0xFF000000) {
                    return y;
                }
            }
        }
        return -1;
    }

    @Test
    @DisplayName("a rising series ends higher than it starts")
    void risingGoesUp() {
        var target = painted(new Sparkline(List.of(0.0, 1.0, 2.0, 3.0)), 40, 20);

        // Column 1 near the left, column 38 near the right: the line's y must
        // decrease, because a chart's y grows downward and the values grow up.
        var left = columnInk(target, 1, 20);
        var right = columnInk(target, 38, 20);

        assertTrue(left > right, "a rising series should be lower on the left; left=" + left + " right=" + right);
    }

    /// The middle row of whatever ink is in column `x`.
    private static int columnInk(TestFrames.Target target, int x, int height) {
        var first = -1;
        var last = -1;
        for (var y = 0; y < height; y++) {
            if (target.pixel(x, y) != 0xFF000000) {
                if (first < 0) {
                    first = y;
                }
                last = y;
            }
        }
        assertTrue(first >= 0, "no ink at all in column " + x);
        return (first + last) / 2;
    }

    @Test
    @DisplayName("scales to the data's own range, not to zero")
    void theRangeIsTheData() {
        // 1000..1004 baselined at zero is a flat line that says nothing. Scaled
        // to its own range it is the same picture as 0..4, which is what a
        // sparkline is for.
        var small = painted(new Sparkline(List.of(0.0, 4.0)), 40, 20);
        var large = painted(new Sparkline(List.of(1000.0, 1004.0)), 40, 20);

        assertEquals(columnInk(small, 1, 20), columnInk(large, 1, 20));
        assertEquals(columnInk(small, 38, 20), columnInk(large, 38, 20));
    }

    @Test
    @DisplayName("a flat series is a line down the middle, not a division by zero")
    void flatIsCentred() {
        var target = painted(new Sparkline(List.of(7.0, 7.0, 7.0, 7.0)), 40, 21);

        var middle = columnInk(target, 20, 21);
        assertTrue(
                Math.abs(middle - 10) <= 1,
                "a series that did not change should be drawn down the middle, and this is at " + middle);
    }

    @Test
    @DisplayName("stays inside its box at the extremes")
    void theStrokeIsInsetByItsOwnWidth() {
        // The maximum is drawn at the top, and a 1.5px line centred on y=0 would
        // lose its upper half to the clip -- a sparkline whose peak looks thinner
        // than the rest of it.
        var target = painted(new Sparkline(List.of(0.0, 10.0, 0.0)), 40, 20);

        assertTrue(topmostInk(target, 40, 20) >= 0, "something was drawn");
        assertEquals(0, topmostInk(target, 40, 20), "the peak reaches the top row but is not clipped through it");
    }

    @Test
    @DisplayName("the last-point marker is a disc, not a square")
    void theMarkerIsRound() {
        // SVG's `A` cannot draw a full circle in one segment -- the start and
        // end points coincide and the arc is undefined -- so the disc is two
        // half-arcs. Getting that wrong produces a square, a wedge or nothing,
        // and all three look plausible in a 200px picture. The corners of the
        // marker's bounding box are what tell them apart.
        var flat = List.of(5.0, 5.0, 5.0);
        var target = painted(new Sparkline(flat, false, true, Attributes.NONE), 40, 21);

        // The marker sits on the last point: the right edge, vertically centred.
        var cx = 40 - 2;
        var cy = 10;
        assertTrue(target.pixel(cx, cy) != 0xFF000000, "the marker's centre is inked");
        // Two pixels out on the diagonal is outside a disc of radius 2 and
        // inside the square that would replace it.
        assertEquals(
                0xFF000000,
                target.pixel(cx - 2, cy - 2),
                "the corner of the marker's box should be empty, and a square would fill it");
    }

    @Test
    @DisplayName("draws nothing for a series that is not a trend")
    void tooFewPointsDrawNothing() {
        for (var values : List.of(List.<Double>of(), List.of(1.0))) {
            var target = painted(new Sparkline(values), 40, 20);
            assertEquals(-1, topmostInk(target, 40, 20), "one point is not a trend and nothing is not a picture");
        }
    }

    @Test
    @DisplayName("takes its colour from CSS, like text")
    void colourIsTheCascade() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "#spark { color: #ff0000 }");
        var tree = new ElementTree(new Sparkline(List.of(0.0, 1.0), false, false, id("spark")));
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        var box = renderer.render(tree)
                .size(
                        io.github.digitalsmile.goldberry.layout.Length.points(40),
                        io.github.digitalsmile.goldberry.layout.Length.points(20));
        assertNotNull(box.painting(), "a sparkline is a canvas");

        // Painted, because "the cascade said red" is only worth asserting if red
        // is what comes out: the colour is read at describe time and closed over
        // by the painter, and the closure is the part that could be wrong.
        var target = TestFrames.of(40, 20, 1.0f);
        try {
            target.frame().fill(0xFF000000);
            BoxPainter.paint(target.frame(), box);
        } finally {
            target.end();
        }
        var ink = target.pixel(1, columnInk(target, 1, 20));
        assertTrue(
                (ink >>> 16 & 0xFF) > 200 && (ink >>> 8 & 0xFF) < 60,
                "the line should be the red the stylesheet asked for, and it is 0x" + Integer.toHexString(ink));
    }

    @Test
    @DisplayName("markup writes one from its arguments")
    void inflatesFromKdl() {
        var widget = Widgets.inflater()
                .inflate(KdlParser.parse("sparkline 4 9 7 12 fill=#true").getFirst());

        assertTrue(widget instanceof Sparkline, "expected a sparkline, got " + widget.getClass());
        var sparkline = (Sparkline) widget;
        assertEquals(List.of(4.0, 9.0, 7.0, 12.0), sparkline.values());
        assertTrue(sparkline.fill());
        assertTrue(!sparkline.marker());
    }

    @Test
    @DisplayName("a hundred thousand points draw the spike a stride would miss")
    void longSeriesKeepTheirShape() {
        var values = new ArrayList<Double>();
        for (var i = 0; i < 100_000; i++) {
            values.add(1.0);
        }
        values.set(63_411, 50.0);

        var target = painted(new Sparkline(values), 200, 40);

        // Without downsampling this is 500 points per pixel and the spike is
        // whichever sample happened to be drawn last. With it, the peak is on
        // screen -- and near the column its index falls in, which is 63.4% of
        // 200 ≈ 127. "Near" rather than "at": LTTB keeps the most *important*
        // point in each bucket, and the bucket is about 500 samples wide, so the
        // sample it keeps is the spike and the column is the spike's.
        assertEquals(0, topmostInk(target, 200, 40), "the spike should reach the top of the box");

        var peakColumn = -1;
        for (var x = 0; x < 200 && peakColumn < 0; x++) {
            if (target.pixel(x, 0) != 0xFF000000) {
                peakColumn = x;
            }
        }
        assertTrue(
                Math.abs(peakColumn - 127) <= 2,
                "the spike is at 63.4% of the series, so about column 127, and it is at " + peakColumn);
    }

    @Test
    @DisplayName("a sparkline under a theme, filled and marked")
    void golden() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame { padding: 12px; gap: 8px; background: var(--gb-bg) }
                #spark { width: 176px; height: 44px; color: var(--gb-accent) }
                """);

        var tree = new ElementTree(new Column(
                List.of(new Sparkline(
                        List.of(4.0, 9.0, 7.0, 12.0, 11.0, 15.0, 13.0, 18.0, 22.0, 19.0), true, true, id("spark"))),
                id("frame")));
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        GoldenImage.assertMatches(
                "sparkline-dark", 200, 68, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
