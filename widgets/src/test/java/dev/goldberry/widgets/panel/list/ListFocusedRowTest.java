package dev.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The row the keyboard is on, in a list inside a `scroll`.
///
/// Through the real router and a painted frame, because both failures this
/// holds were between the list and things it does not own: the viewport took
/// the arrow keys before the list's scope could rove, and the router let go of
/// a focused row that scrolling had unmounted.
class ListFocusedRowTest {

    /// Eight rows of 32.
    private static final int VIEWPORT_HEIGHT = 256;

    private static final double ROW_HEIGHT = 32;

    private static final int COUNT = 10_000;

    private TestFrames.Target target;
    private RenderTree render;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (render != null) {
            render.close();
            render = null;
        }
        if (target != null) {
            target.end();
            target = null;
        }
    }

    /// A window with a router behind its host, so a focus by name lands on an
    /// element and an anchor is a region a frame painted.
    private final class Harness {

        private final PointerRouter router = new PointerRouter();
        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final RoutedHost host;
        private List<HitTest.Region> regions = List.of();

        Harness(Widget root) {
            this(root, "");
        }

        Harness(Widget root, String extraCss) {
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            Theme.NORD_DARK.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, "list { width: 200px }\n" + extraCss)),
                    TestFont.get());
            host = new RoutedHost();
            tree = new ElementTree(root, host);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            frame();
            frame();
        }

        void frame() {
            renderer.prepare(tree);
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            regions = HitTest.capture(render);
            router.updateRegions(regions);
        }

        /// A few frames: the move, the frame that measures where it landed and
        /// the one that draws the window it asked for.
        void settle() {
            for (var i = 0; i < 3; i++) {
                host.tick();
                frame();
            }
        }

        void key(Key key) {
            router.keyPressed(key, Modifiers.NONE, false);
            settle();
        }

        void wheel(float lines) {
            router.pointerWheel(100, VIEWPORT_HEIGHT / 2f, 0, lines, Modifiers.NONE);
            settle();
        }

        void focus(String id) {
            assertTrue(router.focusById(id, true), () -> "no row " + id + " to focus");
            frame();
        }

        @Nullable
        String focused() {
            var element = router.focused();
            return element == null ? null : element.id();
        }

        List<String> rows() {
            var out = new ArrayList<String>();
            collect(tree.root(), element -> {
                if ("list-row".equals(element.type())) {
                    out.add(element.id());
                }
            });
            return out;
        }

        /// Whether the row was painted wholly inside the viewport.
        boolean visible(String id) {
            for (var region : regions) {
                if (region.owner() instanceof Element element && id.equals(element.id())) {
                    var painted = region.painted();
                    return painted.top() >= -0.5
                            && painted.top() + painted.size().height() <= VIEWPORT_HEIGHT + 0.5;
                }
            }
            return false;
        }

        /// A [TestHost] whose focus and anchors are the router's and the frame's.
        private final class RoutedHost extends TestHost {

            @Override
            public boolean focus(String id, boolean fromKeyboard) {
                super.focus(id, fromKeyboard);
                return router.focusById(id, fromKeyboard);
            }

            @Override
            public Optional<HitTest.Region> anchor(String id) {
                for (var region : regions) {
                    if (region.owner() instanceof Element element && id.equals(element.id())) {
                        return Optional.of(region);
                    }
                }
                return Optional.empty();
            }
        }
    }

    private static void collect(Element from, Consumer<Element> to) {
        to.accept(from);
        for (var child : from.children()) {
            collect(child, to);
        }
    }

    private static List<String> names(int count) {
        var out = new ArrayList<String>(count);
        for (var i = 0; i < count; i++) {
            out.add("Row " + i);
        }
        return List.copyOf(out);
    }

    private static Widget scrolled(Widget list) {
        return new Scroll(List.of(list), ScrollAxis.VERTICAL, Attributes.NONE);
    }

    @Nested
    @DisplayName("a virtual list")
    class Virtual {

        private Harness harness() {
            return new Harness(
                    scrolled(ListView.of(names(COUNT)).virtualized(ROW_HEIGHT).id("rows")));
        }

        @Test
        @DisplayName("Down moves the focus to the next row rather than scrolling past it")
        void downMovesTheFocus() {
            var harness = harness();
            harness.focus("rows-Row 2");

            harness.key(Key.DOWN);

            assertEquals("rows-Row 3", harness.focused());
        }

        @Test
        @DisplayName("stepping past the bottom edge brings each row into view")
        void theViewportFollows() {
            var harness = harness();
            harness.focus("rows-Row 0");

            for (var i = 0; i < 30; i++) {
                harness.key(Key.DOWN);
            }

            assertEquals("rows-Row 30", harness.focused());
            assertTrue(harness.visible("rows-Row 30"), "the focused row was stepped to and left out of view");
        }

        @Test
        @DisplayName("Up from the first row wraps to the last, which is built to be focused")
        void upWrapsToTheEnd() {
            var harness = harness();
            harness.focus("rows-Row 0");

            harness.key(Key.UP);
            harness.settle();

            assertEquals("rows-Row 9999", harness.focused());
            assertTrue(harness.visible("rows-Row 9999"));
        }

        @Test
        @DisplayName("a wheel that carries the focused row away keeps it built and focused")
        void theFocusedRowStays() {
            var harness = harness();
            harness.focus("rows-Row 2");

            harness.wheel(400);

            assertEquals("rows-Row 2", harness.focused(), "scrolling away dropped the focus");
            var rows = harness.rows();
            assertTrue(rows.contains("rows-Row 2"), "the focused row was unmounted");
            assertTrue(rows.size() < 40, () -> "pinning built the rows between too: " + rows.size());
        }

        @Test
        @DisplayName("and the next arrow steps from it and scrolls back to where it is")
        void anArrowFromAPinnedRow() {
            var harness = harness();
            harness.focus("rows-Row 2");
            harness.wheel(400);

            harness.key(Key.DOWN);
            harness.settle();

            assertEquals("rows-Row 3", harness.focused());
            assertTrue(harness.visible("rows-Row 3"), "the viewport did not come back to the row");
        }

        @Test
        @DisplayName("End builds the last row on its own, not every row down to it")
        void endBuildsOneRow() {
            var harness = harness();
            harness.focus("rows-Row 0");

            harness.router.keyPressed(Key.END, Modifiers.NONE, false);
            harness.frame();

            var rows = harness.rows();
            assertTrue(rows.contains("rows-Row 9999"));
            assertTrue(rows.size() < 40, () -> "End widened the window to " + rows.size() + " rows");
        }
    }

    @Nested
    @DisplayName("a list whose rows are measured")
    class Measured {

        @Test
        @DisplayName("keeps its focused row through a wheel, and steps from it")
        void theFocusedRowStays() {
            var harness = new Harness(scrolled(ListView.of(names(COUNT))
                    .virtualized(RowHeights.estimating(32))
                    .id("rows")));
            harness.focus("rows-Row 2");

            harness.wheel(400);

            assertEquals("rows-Row 2", harness.focused(), "scrolling away dropped the focus");
            assertTrue(
                    harness.rows().size() < 60,
                    () -> "pinning built " + harness.rows().size() + " rows");

            harness.key(Key.DOWN);
            harness.settle();
            assertEquals("rows-Row 3", harness.focused());
            assertTrue(harness.visible("rows-Row 3"), "the viewport did not come back to the row");
        }
    }

    @Nested
    @DisplayName("a list that builds every row")
    class Unvirtualized {

        @Test
        @DisplayName("steps with the arrows and brings the row into view")
        void theViewportFollows() {
            var harness = new Harness(scrolled(ListView.of(names(40)).id("rows")));
            harness.focus("rows-Row 0");

            for (var i = 0; i < 20; i++) {
                harness.key(Key.DOWN);
            }

            assertEquals("rows-Row 20", harness.focused());
            assertTrue(harness.visible("rows-Row 20"), "the focused row was stepped to and left out of view");
        }
    }

    @Test
    @DisplayName("focus leaving the list lets the pinned row go")
    void leavingReleasesThePin() {
        var harness = new Harness(
                new Column(
                        List.of(
                                new Button("Elsewhere", () -> {}).withAttributes(Attributes.NONE.id("elsewhere")),
                                scrolled(ListView.of(names(COUNT))
                                        .virtualized(ROW_HEIGHT)
                                        .id("rows"))),
                        Attributes.NONE),
                "scroll { height: 200px }");
        harness.focus("rows-Row 2");
        harness.wheel(400);
        assertTrue(harness.rows().contains("rows-Row 2"));

        harness.focus("elsewhere");
        harness.wheel(1);

        assertEquals("elsewhere", harness.focused());
        assertTrue(!harness.rows().contains("rows-Row 2"), "a row nobody is on stayed built");
    }
}
