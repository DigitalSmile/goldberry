package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §1's `scrollIntoView`, shipped as the API §1 words it as
/// ([ADR-0120](../../../../../../../../book/src/adr/0120-a-widget-scrolls-itself-into-view.md)).
class ScrollControllerTest {

    private static final int VIEWPORT_HEIGHT = 100;

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

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();

        Harness(Widget root) {
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            tree = new ElementTree(root);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            frame();
            frame();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// Where the row with `id` is painted, in window coordinates.
        LogicalRect rowRect(String id) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && id.equals(element.id())) {
                    var m = placed.transform();
                    var l = placed.layout();
                    found.add(LogicalRect.of(
                            (float) (m.a() * l.left() + m.c() * l.top() + m.e()),
                            (float) (m.b() * l.left() + m.d() * l.top() + m.f()),
                            l.width(), l.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one row with id " + id);
            return found.getFirst();
        }
    }

    private static Widget document(ScrollController controller, String markedRow) {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < 30; i++) {
            rows.add(new Text("row " + i, Attributes.NONE.id("row" + i)));
        }
        return new Scroll(List.of(new Column(rows.toArray(Widget[]::new))),
                ScrollAxis.VERTICAL, Attributes.NONE).controlledBy(controller);
    }

    @Nested
    @DisplayName("attaching")
    class Attaching {

        @Test
        @DisplayName("a controller with no viewport is inert rather than an error")
        void detachedIsInert() {
            var controller = new ScrollController();

            assertFalse(controller.isAttached());
            // It is perfectly ordinary for a controller to exist before the
            // `Scroll` that answers to it, so this must not throw.
            controller.scrollBy(0, 100);
            controller.reveal(LogicalRect.of(0, 0, 10, 10), LogicalRect.of(0, 0, 10, 10));
        }

        @Test
        @DisplayName("a viewport attaches when it is mounted")
        void attaches() {
            var controller = new ScrollController();
            new Harness(document(controller, null));

            assertTrue(controller.isAttached());
        }
    }

    @Nested
    @DisplayName("scrolling")
    class Scrolling {

        @Test
        @DisplayName("scrollBy moves the viewport, clamped like every other path")
        void scrollByMoves() {
            var controller = new ScrollController();
            var harness = new Harness(document(controller, null));
            var before = harness.rowRect("row0").top();

            controller.scrollBy(0, 40);
            harness.frame();

            assertEquals(before - 40, harness.rowRect("row0").top(), 0.5);
        }

        @Test
        @DisplayName("scrollBy cannot run off the end")
        void scrollByClamps() {
            var controller = new ScrollController();
            var harness = new Harness(document(controller, null));

            controller.scrollBy(0, 10_000);
            harness.frame();
            var atEnd = harness.rowRect("row0").top();
            controller.scrollBy(0, 10_000);
            harness.frame();

            assertEquals(atEnd, harness.rowRect("row0").top(), 0.01);
        }
    }

    @Nested
    @DisplayName("revealing")
    class Revealing {

        @Test
        @DisplayName("a row below the fold is brought to the near edge")
        void revealsFromBelow() {
            var controller = new ScrollController();
            var harness = new Harness(document(controller, null));
            var viewport = LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT);

            controller.reveal(harness.rowRect("row20"), viewport);
            harness.frame();

            var after = harness.rowRect("row20");
            assertTrue(after.top() >= -1 && after.top() + after.size().height() <= VIEWPORT_HEIGHT + 1,
                    "row20 is at " + after.top() + ", still outside the viewport");
        }

        @Test
        @DisplayName("it scrolls the least it can, so the row lands at the edge it came from")
        void minimal() {
            var controller = new ScrollController();
            var harness = new Harness(document(controller, null));

            controller.reveal(harness.rowRect("row20"), LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            harness.frame();

            // Brought *up to* the bottom edge and no further: a reveal that
            // centred its target would throw away everything the user was
            // already looking at, and §1 asks only for it to be in view.
            var after = harness.rowRect("row20");
            assertTrue(after.top() > VIEWPORT_HEIGHT / 2.0,
                    "the row was pulled further than it needed to be; it is at " + after.top());
        }

        @Test
        @DisplayName("a row already in view does not move anything")
        void alreadyVisible() {
            var controller = new ScrollController();
            var harness = new Harness(document(controller, null));
            var before = harness.rowRect("row0").top();

            controller.reveal(harness.rowRect("row1"), LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            harness.frame();

            assertEquals(before, harness.rowRect("row0").top(), 0.01);
        }
    }
}
