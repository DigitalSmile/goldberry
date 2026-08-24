package io.github.digitalsmile.goldberry.widgets.data.linechart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a chart does when a pointer arrives — `charts.md` §3.1's crosshair and
/// tooltip.
///
/// Driven through the **real router**, against the regions a painted frame
/// produced, because that is the seam the feature lives in: the hovered point is
/// resolved from a pointer's local position against the geometry of the frame
/// that was painted ([ADR-0054]), and a test that called the widget's handler by
/// hand would assert the arithmetic and skip the only interesting part.
///
/// The assertions are **pictures compared to pictures** rather than expected
/// coordinates. The hovered index is state and deliberately not readable from
/// outside; what is observable is that hovering draws something, that two
/// pointer positions over one point draw the *same* thing, and that pointing at
/// the axis labels draws nothing. Where the crosshair lands for a given index is
/// [PlotGeometryTest]'s, in both directions.
class ChartHoverTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> two() {
        return List.of(
                Series.of("Downloads", 12, 19, 15, 27, 31, 28, 36),
                Series.of("Installs", 8, 11, 9, 18, 21, 19, 24));
    }

    private static Widget chart() {
        return new Column(List.of(
                new LineChart(two(),
                        List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                        new Attributes("plot", Set.of(), "plot"))),
                new Attributes("frame", Set.of(), "frame"));
    }

    @Test
    @DisplayName("hovering the plot draws a crosshair that was not there before")
    void hoveringDrawsSomething() {
        try (var harness = new Harness(chart())) {
            var quiet = harness.frame();
            var plot = harness.plotRect();

            harness.move(plot.left() + plot.width() / 2, plot.top() + plot.height() / 2);

            assertFalse(java.util.Arrays.equals(quiet, harness.frame()),
                    "a chart nobody is pointing at and one somebody is should not be the"
                            + " same picture");
        }
    }

    @Test
    @DisplayName("a chart nobody has pointed at has no crosshair in it")
    void theRestingStateIsClean() {
        try (var harness = new Harness(chart())) {
            var first = harness.frame();

            // -1 is the resting hover, so a golden image of a chart is the chart
            // rather than the chart plus whatever the last test pointed at.
            assertArrayEquals(first, harness.frame(),
                    "two frames of an untouched chart are one picture");
        }
    }

    @Test
    @DisplayName("two pointer positions over one point draw one picture")
    void thePointerSnapsToAPoint() {
        try (var harness = new Harness(chart())) {
            harness.frame();
            var plot = harness.plotRect();
            var middle = plot.top() + plot.height() / 2;

            harness.move(plot.left() + plot.width() / 2, middle);
            var first = harness.frame();
            harness.move(plot.left() + plot.width() / 2 + 1, middle);

            // Seven points across ~200px, so a pixel is a fortieth of the way to
            // the next one. A crosshair that moved with the pointer rather than
            // snapping to a point would be reading a position off a chart that
            // has none between its points.
            assertArrayEquals(first, harness.frame(),
                    "one pixel of pointer movement inside one point is no change at all");
        }
    }

    @Test
    @DisplayName("the two ends of the axis read differently")
    void differentPointsReadDifferently() {
        try (var harness = new Harness(chart())) {
            harness.frame();
            var plot = harness.plotRect();
            var middle = plot.top() + plot.height() / 2;

            harness.move(plot.left() + plot.width() * 0.1f, middle);
            var left = harness.frame();
            harness.move(plot.left() + plot.width() * 0.9f, middle);

            assertFalse(java.util.Arrays.equals(left, harness.frame()),
                    "Monday and Sunday are not the same readout");
        }
    }

    @Test
    @DisplayName("pointing at the axis labels is not pointing at a point")
    void theGutterIsNotHoverable() {
        try (var harness = new Harness(chart())) {
            var quiet = harness.frame();
            var plot = harness.plotRect();

            // Two pixels in from the plot's left edge is the y label gutter. A
            // crosshair that snapped to Monday whenever the pointer crossed the
            // numbers would be a chart reacting to being read.
            harness.move(plot.left() + 2, plot.top() + plot.height() / 2);

            assertArrayEquals(quiet, harness.frame(),
                    "the gutter is the axis, not the plot");
        }
    }

    @Test
    @DisplayName("the crosshair goes when the pointer leaves")
    void leavingClearsIt() {
        try (var harness = new Harness(chart())) {
            var quiet = harness.frame();
            var plot = harness.plotRect();

            harness.move(plot.left() + plot.width() / 2, plot.top() + plot.height() / 2);
            harness.frame();
            harness.exit();

            assertArrayEquals(quiet, harness.frame(),
                    "a chart the pointer has left is the chart again");
        }
    }

    @Test
    @DisplayName("a crosshair, a marker per series, and the readout beside it")
    void golden() {
        try (var harness = new Harness(chart())) {
            harness.frame();
            var plot = harness.plotRect();
            // Seven tenths along, so the crosshair is in the **right** half and
            // the readout flips to the left of it rather than being clipped by
            // the edge it is approaching. Not the exact middle: that is the
            // pixel the flip is decided on, and a golden should not be a
            // photograph of a tie-break.
            harness.move(plot.left() + plot.width() * 0.7f, plot.top() + plot.height() / 2);

            GoldenImage.assertMatches("line-chart-hover-dark", WIDTH, HEIGHT, 1.0f,
                    harness::paintInto);
        }
    }

    @Test
    @DisplayName("a bar chart highlights the band, because a bar owns a width")
    void barsHighlightTheirBand() {
        var bars = new Column(List.of(
                new io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart(
                        two(), List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                        new Attributes("plot", Set.of(), "plot"))),
                new Attributes("frame", Set.of(), "frame"));

        try (var harness = new Harness(bars)) {
            harness.frame();
            var plot = harness.plotRect();
            // A hairline down the middle of a *group* of bars points at the gap
            // between two of them, so this is a rectangle and there are no
            // markers: the highlight says "this category", which is what a bar
            // chart's x actually is. Three tenths along, so the readout sits on
            // the right and this picture is the mirror of the line chart's.
            harness.move(plot.left() + plot.width() * 0.3f, plot.top() + plot.height() / 2);

            GoldenImage.assertMatches("bar-chart-hover-dark", WIDTH, HEIGHT, 1.0f,
                    harness::paintInto);
        }
    }

    /// A chart in a window, painted, with a router over the frame it produced.
    ///
    /// Every step is here because the feature needs it: `render` to build the
    /// boxes, `update` to lay them out, **`paint` to bank the geometry** — which
    /// is the step a hover cannot work without — and `updateRegions` to give the
    /// router something to route against.
    private static final class Harness implements AutoCloseable {

        private final WidgetRenderer renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg) }
                                #plot  { width: 296px; height: 156px }
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

        /// Paints a frame and reads it back.
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

        /// The same, into a frame somebody else owns — which is what
        /// [GoldenImage] hands over.
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

        /// Where the `chart-plot` was painted, in window coordinates.
        LogicalRect plotRect() {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && element.widget() instanceof ChartSurface) {
                    var layout = placed.layout();
                    var matrix = placed.transform();
                    found.add(LogicalRect.of(
                            (float) (matrix.a() * layout.left()
                                    + matrix.c() * layout.top() + matrix.e()),
                            (float) (matrix.b() * layout.left()
                                    + matrix.d() * layout.top() + matrix.f()),
                            layout.width(), layout.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one chart-plot");
            return found.getFirst();
        }

        @Override
        public void close() {
            render.close();
        }
    }
}
