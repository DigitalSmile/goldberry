package dev.goldberry.widgets.data.linechart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.data.Series;

/// Clicking a legend entry isolates its series, and clicking again restores the
/// rest — the one interaction Grafana users reach for first.
///
/// Driven through the real router, for [ChartHoverTest]'s reason: the click
/// arrives at the **legend** and changes what the **plot** draws, and those are
/// siblings, so the interesting part is the state above both of them and not
/// either handler on its own.
///
/// Read more: [What the five share](https://goldberry.dev/docs/components/charts.html#what-the-five-share).
class LegendIsolationTest {

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

    private static Widget chart() {
        return new Column(
                List.of(new LineChart(
                        two(),
                        List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                        new Attributes("plot", Set.of(), "plot"))),
                new Attributes("frame", Set.of(), "frame"));
    }

    @Test
    @DisplayName("clicking an entry shows its series alone")
    void aClickIsolates() {
        try (var harness = new Harness(chart())) {
            var everything = harness.frame();

            harness.click(harness.entryRect(1));

            assertFalse(
                    java.util.Arrays.equals(everything, harness.frame()),
                    "one line and two lines are not the same picture");
        }
    }

    @Test
    @DisplayName("clicking the same entry again puts them all back")
    void theSecondClickIsTheWayBack() {
        try (var harness = new Harness(chart())) {
            var everything = harness.frame();

            harness.click(harness.entryRect(0));
            harness.frame();
            harness.click(harness.entryRect(0));

            // A control that isolated on click and needed something else to
            // restore would be one with no visible way out of the state it just
            // entered.
            assertArrayEqualsWithMessage(everything, harness.frame(), "the chart came back exactly as it was");
        }
    }

    @Test
    @DisplayName("clicking the other entry moves the isolation rather than clearing it")
    void isolationMoves() {
        try (var harness = new Harness(chart())) {
            harness.frame();

            harness.click(harness.entryRect(0));
            var first = harness.frame();
            harness.click(harness.entryRect(1));

            assertFalse(
                    java.util.Arrays.equals(first, harness.frame()), "isolating Installs is not isolating Downloads");
        }
    }

    @Test
    @DisplayName("the entries nobody isolated are dimmed, not removed")
    void theOthersAreMutedAndStayPut() {
        try (var harness = new Harness(chart())) {
            harness.frame();
            var before = harness.entryRect(1);

            harness.click(harness.entryRect(0));
            harness.frame();

            // A legend that dropped the hidden series would change width as you
            // clicked it, and the way back would go with them.
            assertEquals(before, harness.entryRect(1), "the second entry is where it was");
            assertTrue(harness.entryClasses(1).contains("muted"), "and it says it is not currently drawn");
            assertFalse(harness.entryClasses(0).contains("muted"), "while the isolated one does not");
        }
    }

    @Test
    @DisplayName("an isolated series keeps its own colour and rescales the axis")
    void golden() {
        try (var harness = new Harness(chart())) {
            harness.frame();
            harness.click(harness.entryRect(1));

            // Installs alone: still the **second** palette slot, because the
            // index is the colour and filtering the list would recolour it
            // -- and on an axis relabelled to its own range, which is
            // the point of asking for it alone.
            GoldenImage.assertMatches("line-chart-isolated-dark", WIDTH, HEIGHT, 1.0f, harness::paintInto);
        }
    }

    private static void assertArrayEqualsWithMessage(int[] expected, int[] actual, String why) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual, why);
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

        void paintInto(dev.goldberry.paint.Frame frame) {
            tree.flush();
            render.update(frame, renderer.render(tree));
            render.paint(frame);
            router.updateRegions(HitTest.capture(render));
        }

        /// A press and a release in the middle of `rect`, which is what a click
        /// is: the router synthesizes `CLICKED` from the pair, and a handler
        /// listening for the release instead would fire on a drag the user
        /// cancelled.
        void click(LogicalRect rect) {
            var x = rect.left() + rect.width() / 2;
            var y = rect.top() + rect.height() / 2;
            router.pointerMoved(x, y);
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);
        }

        /// Where legend entry `slot` was painted, in window coordinates.
        LogicalRect entryRect(int slot) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (entry(placed.box()) instanceof ChartLegendEntry candidate && candidate.slot() == slot) {
                    var layout = placed.layout();
                    var matrix = placed.transform();
                    found.add(LogicalRect.of(
                            (float) (matrix.a() * layout.left() + matrix.c() * layout.top() + matrix.e()),
                            (float) (matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f()),
                            layout.width(),
                            layout.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one legend entry in slot " + slot);
            return found.getFirst();
        }

        /// What the cascade was told about entry `slot` — the classes it is
        /// selected by.
        Set<String> entryClasses(int slot) {
            var found = new ArrayList<Set<String>>();
            render.forEachPlacedBox(placed -> {
                if (entry(placed.box()) instanceof ChartLegendEntry candidate && candidate.slot() == slot) {
                    found.add(candidate.classes());
                }
            });
            assertEquals(1, found.size(), "expected exactly one legend entry in slot " + slot);
            return found.getFirst();
        }

        private static Widget entry(Box box) {
            return box.owner() instanceof Element element ? element.widget() : null;
        }

        @Override
        public void close() {
            render.close();
        }
    }
}
