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
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
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
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// One crosshair across two charts — `charts.md` §3.1's shared crosshair.
///
/// Driven through the real router, because the claim is about what happens to
/// the chart the pointer is **not** over, and nothing short of a dispatch
/// produces that.
class CrosshairGroupTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 260;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a listener hears a move, and only when it is one")
    void theHolderIsSimple() {
        var group = new CrosshairGroup();
        var heard = new int[1];

        var subscription = group.subscribe(() -> heard[0]++);
        group.hover(3);
        group.hover(3);
        group.hover(4);
        group.clear();

        // Three changes and one repeat: a pointer crossing a point sends an event
        // per pixel, and a notification per event would rebuild every chart on
        // the dashboard sixty times a second to draw the same line.
        assertEquals(3, heard[0]);
        assertEquals(-1, group.hovered());
        subscription.close();
        group.hover(9);
        assertEquals(3, heard[0], "and nothing after it was closed");
    }

    @Test
    @DisplayName("a chart registers with its group and lets go when it is disposed")
    void nothingIsHeldAlive() {
        var group = new CrosshairGroup();
        try (var harness = new Harness(two(group, group))) {
            harness.frame();
            assertEquals(2, group.listenerCount(), "both charts are listening");
        }
        // A chart that stayed subscribed would hold the group's listener list --
        // and through it the last window's charts -- alive, and would be rebuilt
        // by a crosshair it no longer draws.
        var alone = new Harness(two(group, null));
        alone.frame();
        assertEquals(1, group.listenerCount(), "and only the one that is in it");
        alone.close();
    }

    @Test
    @DisplayName("pointing at one chart moves the other's crosshair")
    void theWholePointOfIt() {
        var group = new CrosshairGroup();
        try (var harness = new Harness(two(group, group))) {
            var quiet = harness.frame();
            var first = harness.plotRect(0);

            harness.move(first.left() + first.width() * 0.7f, first.top() + first.height() / 2);

            assertFalse(java.util.Arrays.equals(quiet, harness.frame()), "the chart under the pointer drew something");
            assertTrue(group.hovered() > 0, "and told the group where it was");
            // The second chart's own pixels changed, which is the claim: it is
            // showing a crosshair for a pointer that is not in it.
            assertFalse(
                    java.util.Arrays.equals(
                            harness.crop(quiet, harness.plotRect(1)),
                            harness.crop(harness.frame(), harness.plotRect(1))),
                    "and so did the chart the pointer is not over");
        }
    }

    @Test
    @DisplayName("and ungrouped charts stay out of it")
    void nothingLeaksToACharNotInTheGroup() {
        var group = new CrosshairGroup();
        try (var harness = new Harness(two(group, null))) {
            var quiet = harness.frame();
            var first = harness.plotRect(0);

            harness.move(first.left() + first.width() * 0.7f, first.top() + first.height() / 2);

            assertArrayEquals(
                    harness.crop(quiet, harness.plotRect(1)),
                    harness.crop(harness.frame(), harness.plotRect(1)),
                    "a chart that is not in the group did not move");
        }
    }

    @Test
    @DisplayName("only the chart under the pointer says what the numbers are")
    void theReadoutStaysWhereTheReaderIs() {
        // Six floating boxes on a dashboard, five of them about a chart nobody is
        // pointing at, is worse than no linking at all. The linked chart draws
        // the line and not the box, so its own change is small.
        var group = new CrosshairGroup();
        try (var harness = new Harness(two(group, group))) {
            var quiet = harness.frame();
            var first = harness.plotRect(0);
            harness.move(first.left() + first.width() * 0.7f, first.top() + first.height() / 2);
            var moved = harness.frame();

            var pointed = differing(harness.crop(quiet, harness.plotRect(0)), harness.crop(moved, harness.plotRect(0)));
            var linked = differing(harness.crop(quiet, harness.plotRect(1)), harness.crop(moved, harness.plotRect(1)));

            assertTrue(linked > 0, "the linked chart drew its crosshair");
            assertTrue(pointed > linked * 2, "the pointed chart drew a readout as well: " + pointed + " vs " + linked);
        }
    }

    private static int differing(int[] a, int[] b) {
        var count = 0;
        for (var i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                count++;
            }
        }
        return count;
    }

    /// Two charts in a column, each in `first`/`second`'s group or in none.
    private static Widget two(CrosshairGroup first, CrosshairGroup second) {
        return new Column(
                List.of(chart("plot-a", first), chart("plot-b", second)), new Attributes("frame", Set.of(), "frame"));
    }

    private static Widget chart(String id, CrosshairGroup group) {
        var chart = new LineChart(
                List.of(Series.of("rate", 12, 19, 15, 27, 31, 28, 36)),
                List.of(),
                new Attributes(id, Set.of("plot"), id));
        return group == null ? chart : chart.crosshair(group);
    }

    /// A window with two charts in it, painted, with a router over the frame.
    private static final class Harness implements AutoCloseable {

        private final WidgetRenderer renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg); gap: 12px }
                                .plot  { width: 296px; height: 104px }
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
                tree.flush();
                render.update(target.frame(), renderer.render(tree));
                render.paint(target.frame());
                router.updateRegions(HitTest.capture(render));
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

        void move(float x, float y) {
            router.pointerMoved(x, y);
        }

        /// The `index`th `chart-plot`'s painted rectangle.
        LogicalRect plotRect(int index) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                        && "chart-plot".equals(styled.cssType())) {
                    var layout = placed.layout();
                    found.add(LogicalRect.of(layout.left(), layout.top(), layout.width(), layout.height()));
                }
            });
            assertEquals(2, found.size(), "two plots");
            return found.get(index);
        }

        int[] crop(int[] frame, LogicalRect rect) {
            var left = Math.round(rect.left());
            var top = Math.round(rect.top());
            var width = Math.round(rect.width());
            var height = Math.round(rect.height());
            var out = new int[width * height];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    out[y * width + x] = frame[(top + y) * WIDTH + (left + x)];
                }
            }
            return out;
        }

        @Override
        public void close() {
            // **Unmounted, not just closed.** `dispose()` is what unsubscribes a
            // chart from its group, and a window that went away without it is
            // exactly the leak this file is about.
            tree.unmount();
            render.close();
        }
    }
}
