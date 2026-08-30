package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A donut answering the pointer, and every chart answering the keyboard.
///
/// `charts.md` §3.5 is the reason the second half of this file exists: "a browser
/// dashboard is a pointer surface; a desktop application is not, and §2.2
/// requires everything to be reachable. Grafana is weak here and it is not a
/// model to copy."
///
/// The keyboard assertions are the interesting shape — **what a key does is
/// compared against what the mouse does**, so the two paths are held to one
/// answer rather than to two descriptions of one.
class ChartInputTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> two() {
        return List.of(
                Series.of("Downloads", 12, 19, 15, 27, 31, 28, 36), Series.of("Installs", 8, 11, 9, 18, 21, 19, 24));
    }

    private static Widget lineChart() {
        return framed(new LineChart(
                two(),
                List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                new Attributes("plot", Set.of(), "plot")));
    }

    private static Widget donut() {
        return framed(new DonutChart(
                List.of(Series.of("Cache", 62), Series.of("Origin", 24), Series.of("Miss", 14)),
                new Attributes("plot", Set.of(), "plot")));
    }

    private static Widget framed(Widget chart) {
        return new Column(List.of(chart), new Attributes("frame", Set.of(), "frame"));
    }

    /// A window coordinate on the ring of a donut painted in `plot`, `turns` of a
    /// full circle clockwise from twelve o'clock.
    ///
    /// Derived rather than guessed, because a donut's ring is as wide as the box
    /// is **tall**: a fraction of the width lands in the corner of a plot that is
    /// wider than it is high, which is every chart in a dashboard.
    private static float[] onTheRing(LogicalRect plot, double turns) {
        var cx = plot.left() + plot.width() / 2;
        var cy = plot.top() + plot.height() / 2;
        // Between the hole at 0.62 and the outer edge at 1.
        var radius = Math.min(plot.width(), plot.height()) / 2 * 0.81;
        var angle = -Math.PI / 2 + turns * Math.PI * 2;
        return new float[] {
            (float) (cx + radius * Math.cos(angle)), (float) (cy + radius * Math.sin(angle)),
        };
    }

    @Nested
    @DisplayName("a donut under the pointer")
    class DonutPointer {

        @Test
        @DisplayName("hovering a slice fades the others and writes in the hole")
        void hoveringASliceReadsIt() {
            try (var harness = new Harness(donut())) {
                var quiet = harness.frame();
                var plot = harness.plotRect("donut-plot");

                var point = onTheRing(plot, 0.15);
                harness.move(point[0], point[1]);

                assertFalse(
                        java.util.Arrays.equals(quiet, harness.frame()),
                        "a donut nobody is reading and one somebody is are different pictures");
            }
        }

        @Test
        @DisplayName("the hole is not the chart")
        void theHoleIsNotHoverable() {
            try (var harness = new Harness(donut())) {
                var quiet = harness.frame();
                var plot = harness.plotRect("donut-plot");

                // A readout that appeared when the pointer crossed the middle
                // would be a donut reacting to the space it deliberately left
                // empty.
                harness.move(plot.left() + plot.width() / 2, plot.top() + plot.height() / 2);

                assertArrayEquals(quiet, harness.frame(), "the hole reads nothing");
            }
        }

        @Test
        @DisplayName("leaving the ring puts the donut back")
        void leavingClearsIt() {
            try (var harness = new Harness(donut())) {
                var quiet = harness.frame();
                var plot = harness.plotRect("donut-plot");

                var point = onTheRing(plot, 0.15);
                harness.move(point[0], point[1]);
                assertFalse(java.util.Arrays.equals(quiet, harness.frame()), "the pointer really did land on the ring");
                harness.exit();

                assertArrayEquals(quiet, harness.frame());
            }
        }

        @Test
        @DisplayName("a slice read, its share in the hole and the rest faded")
        void golden() {
            try (var harness = new Harness(donut())) {
                harness.frame();
                var plot = harness.plotRect("donut-plot");
                // Three o'clock, which is the middle of a 62% slice that started
                // at twelve.
                var point = onTheRing(plot, 0.25);
                harness.move(point[0], point[1]);

                GoldenImage.assertMatches("donut-chart-hover-dark", WIDTH, HEIGHT, 1.0f, harness::paintInto);
            }
        }
    }

    @Nested
    @DisplayName("a chart that has been scrolled")
    class Scrolled {

        /// `plot` without the strip a scroll bar sits over.
        ///
        /// The bar is the viewport's and is drawn on top of whatever is under it,
        /// so it is a real difference between the two windows and not one about
        /// the chart.
        private LogicalRect readable(LogicalRect plot) {
            return LogicalRect.of(plot.left(), plot.top(), plot.width() - 16, plot.height());
        }

        /// The chart, pushed down a viewport so that reading it means scrolling
        /// first — which is every chart on a dashboard taller than its window.
        private Widget inAViewport() {
            return new Column(
                    List.of(new Scroll(
                            List.of(new Column(
                                    List.of(
                                            new Text("filler", new Attributes("spacer", Set.of(), "spacer")),
                                            new LineChart(
                                                    two(),
                                                    List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                                                    new Attributes("plot", Set.of(), "plot"))),
                                    Attributes.NONE)),
                            ScrollAxis.VERTICAL,
                            new Attributes("viewport", Set.of(), "viewport"))),
                    new Attributes("frame", Set.of(), "frame"));
        }

        @Test
        @DisplayName("still highlights the point under the pointer")
        void scrollingDoesNotBreakTheCrosshair() {
            try (var harness = new Harness(inAViewport())) {
                harness.frame();
                // Down far enough that the plot is on screen at all -- the
                // spacer above it is taller than the viewport -- and therefore
                // drawn a long way from where it was laid out.
                harness.wheel(6);
                harness.frame();
                var plot = harness.plotRect("chart-plot");
                // The plot's own pixels, so that a scroll bar reacting to the
                // same pointer cannot make this pass on its own.
                var quiet = harness.crop(readable(plot));

                harness.move(plot.left() + plot.width() * 0.6f, plot.top() + plot.height() / 2);

                // A `scroll` moves its content with a transform, so the plot is
                // laid out where it always was and painted somewhere else. The
                // pointer reached it either way -- hit testing has always undone
                // the matrix -- and *where inside* did not, so the crosshair
                // stopped the moment the panel moved.
                assertFalse(
                        java.util.Arrays.equals(quiet, harness.crop(readable(plot))),
                        "a scrolled chart still answers the pointer");
            }
        }

        @Test
        @DisplayName("and highlights exactly what the same chart highlights on its own")
        void theSamePointEitherWay() {
            // The plot's **own pixels**, cropped out of each frame: the two
            // windows differ everywhere else, and the claim is about the chart.
            int[] alone;
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                var plot = harness.plotRect("chart-plot");
                harness.move(plot.left() + plot.width() * 0.6f, plot.top() + plot.height() / 2);
                harness.frame();
                alone = harness.crop(readable(plot));
            }

            try (var harness = new Harness(inAViewport())) {
                harness.frame();
                // To the bottom, where the whole plot is on screen.
                harness.wheel(20);
                harness.frame();
                var plot = harness.plotRect("chart-plot");
                harness.move(plot.left() + plot.width() * 0.6f, plot.top() + plot.height() / 2);
                harness.frame();

                // Six tenths across is the same point wherever the panel has been
                // scrolled to. Before the fix this crop was a chart with no
                // crosshair on it at all.
                assertArrayEquals(
                        alone,
                        harness.crop(readable(plot)),
                        "the point under the pointer does not depend on the scroll offset");
            }
        }
    }

    @Nested
    @DisplayName("an axis chart under the keyboard")
    class AxisKeyboard {

        @Test
        @DisplayName("a chart with data is a Tab stop, and an empty one is not")
        void reachableWithoutAPointer() {
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                assertTrue(harness.focus("chart-plot"), "§2.2: everything is reachable");
            }
            try (var harness = new Harness(framed(new LineChart(List.of())))) {
                harness.frame();
                assertFalse(harness.focus("chart-plot"), "a chart with nothing in it has nothing to walk");
            }
        }

        @Test
        @DisplayName("End is the last point, and it is the mouse's answer too")
        void theKeyboardAndTheMouseAgree() {
            int[] byKey;
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");
                harness.press(Key.END);
                byKey = harness.frame();
            }

            try (var harness = new Harness(lineChart())) {
                harness.frame();
                // Focused here too, so the two pictures differ in the crosshair
                // and not in the focus ring -- which is a real difference and not
                // the one under test.
                harness.focus("chart-plot");
                harness.frame();
                var plot = harness.plotRect("chart-plot");
                // The right-hand edge, which is the last point whatever the label
                // gutter turned out to be -- the left-hand edge is the axis.
                harness.move(plot.left() + plot.width() - 1, plot.top() + plot.height() / 2);

                // The two paths have to produce one state, or a chart read with a
                // keyboard is a different chart.
                assertArrayEquals(byKey, harness.frame(), "the last point by key and the last point by mouse");
            }
        }

        @Test
        @DisplayName("End is the last point, Home is the first, and Escape lets go")
        void theEndsAndTheWayOut() {
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");
                // Captured **after** focusing, because a focused plot wears the
                // focus ring and that is part of the picture.
                var quiet = harness.frame();

                harness.press(Key.END);
                var last = harness.frame();
                harness.press(Key.HOME);
                var first = harness.frame();

                assertFalse(java.util.Arrays.equals(last, first), "Sunday and Monday are not the same readout");

                harness.press(Key.ESCAPE);
                assertArrayEquals(quiet, harness.frame(), "Escape is the way back to a chart with no crosshair on it");
            }
        }

        @Test
        @DisplayName("the crosshair stops at the ends rather than wrapping round")
        void theAxisHasTwoEnds() {
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");

                harness.press(Key.END);
                var last = harness.frame();
                harness.press(Key.RIGHT);

                // A line has two ends; a crosshair that jumped back to Monday
                // after Sunday would be a chart pretending its axis is a circle.
                assertArrayEquals(last, harness.frame(), "Right at the last point stays");
            }
        }

        @Test
        @DisplayName("Up and Down are left for whatever is scrolling")
        void theVerticalArrowsAreNotTaken() {
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");
                var quiet = harness.frame();

                // A focused widget that consumed the vertical arrows would
                // swallow the keys that move the page it is on.
                assertFalse(harness.press(Key.DOWN), "Down is not the chart's");
                assertFalse(harness.press(Key.UP), "nor is Up");
                assertArrayEquals(quiet, harness.frame());
            }
        }

        @Test
        @DisplayName("two arrows between two frames move the crosshair twice")
        void aStepIsRelativeToWhereItActuallyIs() {
            // Two presses with **no frame between them**, which is what a fast
            // key repeat produces.
            int[] frameless;
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");
                harness.press(Key.RIGHT);
                harness.press(Key.RIGHT);
                frameless = harness.frame();
            }

            // The same two presses with a frame in between, which cannot be got
            // wrong: two moves.
            int[] spaced;
            int[] onlyOne;
            try (var harness = new Harness(lineChart())) {
                harness.frame();
                harness.focus("chart-plot");
                harness.press(Key.RIGHT);
                onlyOne = harness.frame();
                harness.press(Key.RIGHT);
                spaced = harness.frame();
            }

            // A key press needs the crosshair's current position, and a widget is
            // the description the *last* frame was built from -- so a widget that
            // worked the step out for itself would compute both presses from the
            // same stale index and move once. The state owns the step for exactly
            // this reason, and this is the assertion that says so.
            assertFalse(java.util.Arrays.equals(frameless, onlyOne), "two arrows in one frame are not one arrow");
            assertArrayEquals(spaced, frameless, "and they are the same two steps a frame apart");
        }

        @Test
        @DisplayName("a donut's arrows walk its slices, and wrap because a ring has no ends")
        void aRingWrapsWhereAnAxisStops() {
            try (var harness = new Harness(donut())) {
                harness.frame();
                harness.focus("donut-plot");

                harness.press(Key.RIGHT);
                var first = harness.frame();
                harness.press(Key.RIGHT);
                assertFalse(java.util.Arrays.equals(first, harness.frame()), "the second slice is not the first");

                harness.press(Key.RIGHT);
                harness.press(Key.RIGHT);

                // Three slices, so the fourth Right is back to the first -- and
                // the third and fourth are pressed **without a frame between
                // them**, which is what a fast key repeat does and what caught
                // the widget reading its own stale index.
                assertArrayEquals(first, harness.frame(), "round the ring and back");
            }
        }
    }

    /// A chart in a window, painted, with a router over the frame it produced.
    private static final class Harness implements AutoCloseable {

        private final WidgetRenderer renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg) }
                                #plot  { width: 296px; height: 156px }
                                #viewport { height: 160px }
                                #spacer { height: 120px }
                                """)),
                TestFont.get());
        private final ElementTree tree;
        private final RenderTree render = RenderTree.create();
        private final PointerRouter router = new PointerRouter();

        Harness(Widget root) {
            tree = new ElementTree(root);
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, WIDTH, HEIGHT));
        }

        int[] frame() {
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            try {
                paintInto(target.frame());
            } finally {
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

        void paintInto(io.github.digitalsmile.goldberry.paint.Frame frame) {
            tree.flush();
            render.update(frame, renderer.render(tree));
            render.paint(frame);
            router.updateRegions(HitTest.capture(render));
        }

        void move(float x, float y) {
            router.pointerMoved(x, y);
        }

        void exit() {
            router.pointerExited();
        }

        /// The pixels inside `rect`, which is how two windows that differ
        /// everywhere else are compared over the one widget they share.
        int[] crop(LogicalRect rect) {
            var whole = frame();
            var left = Math.round(rect.left());
            var top = Math.round(rect.top());
            var width = Math.round(rect.width());
            var height = Math.round(rect.height());
            var out = new int[width * height];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    out[y * width + x] = whole[(top + y) * WIDTH + (left + x)];
                }
            }
            return out;
        }

        /// Turns the wheel over the middle of the window, then lets the next
        /// frame apply it.
        void wheel(float lines) {
            router.pointerWheel(WIDTH / 2f, HEIGHT / 2f, 0, lines, Modifiers.NONE);
        }

        /// Puts the keyboard on the part named `cssType`, as a Tab traversal
        /// would — and answers whether the widget accepted it.
        boolean focus(String cssType) {
            var element = elementOf(cssType);
            if (element == null
                    || !(element.widget() instanceof io.github.digitalsmile.goldberry.input.handler.Handles handles)
                    || !handles.isFocusable()) {
                return false;
            }
            router.focus(element, true);
            return true;
        }

        /// A key down and up, and whether anything consumed the press.
        boolean press(Key key) {
            var consumed = router.keyPressed(key, Modifiers.NONE, false);
            router.keyReleased(key, Modifiers.NONE);
            return consumed;
        }

        /// Where the part named `cssType` was painted, in window coordinates.
        LogicalRect plotRect(String cssType) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                        && cssType.equals(styled.cssType())) {
                    var layout = placed.layout();
                    var matrix = placed.transform();
                    found.add(LogicalRect.of(
                            (float) (matrix.a() * layout.left() + matrix.c() * layout.top() + matrix.e()),
                            (float) (matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f()),
                            layout.width(),
                            layout.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one " + cssType);
            return found.getFirst();
        }

        private Element elementOf(String cssType) {
            return search(tree.root(), cssType);
        }

        private static Element search(Element element, String cssType) {
            if (element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                    && cssType.equals(styled.cssType())) {
                return element;
            }
            for (var child : element.children()) {
                var found = search(child, cssType);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        public void close() {
            render.close();
        }
    }
}
