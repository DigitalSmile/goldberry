package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
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

/// `scroll` — the viewport three separate pieces of work were waiting on
/// ([ADR-0116](../../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
///
/// Everything here needs a **painted frame** before it means anything, and that
/// is the point rather than an inconvenience: a scroll view is arithmetic on two
/// rectangles neither of which exists until Yoga has run, so a test that poked
/// the widget directly would be testing a calculation nobody performs.
class ScrollTest {

    /// A viewport 100 tall over content that is not, which is the only
    /// interesting shape a scroll view has.
    private static final int VIEWPORT_HEIGHT = 100;

    /// Enough rows to overflow it comfortably. Twenty lines of ~16 is about 315,
    /// so there are roughly 215 to travel — chosen with room to spare because
    /// content that *just* overflows makes every clamp assertion below turn on
    /// the test font's exact metrics rather than on the widget.
    private static final int ROWS = 20;

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

    /// A live scroll view: rendered, laid out, and with a router holding the
    /// regions that paint produced.
    ///
    /// The regions are what make the whole thing work — the router reads both
    /// extents out of them — so a harness that skipped the paint would report a
    /// viewport of zero and a scroll view that never moves.
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
            frame();
        }

        /// One frame: rebuild whatever went dirty, render, lay out, and hand the
        /// router the rectangles. Exactly what a window does.
        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// The `scroll` element — the viewport, not the composition node above it.
        Element viewport() {
            return find(tree.root(), "scroll");
        }

        /// Where the content has been moved to, read off the paint rather than
        /// off the widget: the offset is only real if it reached the screen.
        double contentTop() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && "scroll-content".equals(element.type())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    found.add(matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f());
                }
            });
            assertEquals(1, found.size(), "expected exactly one scroll-content");
            return found.getFirst();
        }

        /// Turns the wheel over the middle of the viewport, then paints.
        void wheel(float lines) {
            router.pointerWheel(100, 50, 0, lines, Modifiers.NONE);
            frame();
        }

        /// Presses `key` with the viewport focused, then paints.
        void press(Key key) {
            router.focus(viewport(), true);
            router.keyPressed(key, Modifiers.NONE, false);
            frame();
        }
    }

    private static Element find(Element from, String type) {
        if (type.equals(from.type())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// A column of [#ROWS] lines, which overflows the viewport several times over.
    private static Widget tallContent() {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < ROWS; i++) {
            rows.add(new Text("row " + i));
        }
        return new Scroll(List.of(new Column(rows.toArray(Widget[]::new))),
                ScrollAxis.VERTICAL, io.github.digitalsmile.goldberry.widget.Attributes.NONE);
    }

    @Nested
    @DisplayName("the wheel")
    class Wheel {

        @Test
        @DisplayName("a scroll view is built from a scroll and a scroll-content")
        void structure() {
            var harness = new Harness(tallContent());

            assertNotNull(harness.viewport(), "no element of type scroll");
            assertNotNull(find(harness.viewport(), "scroll-content"),
                    "the viewport built no content node");
        }

        @Test
        @DisplayName("one line down moves the content up by one line's worth")
        void oneLine() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.wheel(1);

            // Up, not down: scrolling down the document moves the content the
            // other way, and a sign error here is the classic scroll bug.
            assertEquals(before - ScrollViewport.LINE, harness.contentTop(), 0.5);
        }

        @Test
        @DisplayName("a fractional line moves a fractional distance, because a touchpad sends those")
        void fractional() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.wheel(0.25f);

            // The whole point of ADR-0115's float reaching this far: rounding
            // here is what makes a trackpad scroll in jerks.
            assertEquals(before - ScrollViewport.LINE * 0.25, harness.contentTop(), 0.5);
        }

        @Test
        @DisplayName("it stops at the bottom rather than running off it")
        void clampsAtTheEnd() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            // Far more than the 140 there is to travel.
            harness.wheel(100);
            var atEnd = harness.contentTop();
            harness.wheel(100);

            assertEquals(atEnd, harness.contentTop(), 0.01,
                    "a second scroll past the end moved it further");
            // §2.4: hard edges, no overscroll bounce. The content's bottom is
            // level with the viewport's, so it has travelled exactly its own
            // overflow and not a pixel more.
            assertTrue(before - atEnd > 0, "it did not move at all");
        }

        @Test
        @DisplayName("it stops at the top, which it starts at")
        void clampsAtTheStart() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.wheel(-5);

            assertEquals(before, harness.contentTop(), 0.01);
        }

        @Test
        @DisplayName("content shorter than its viewport does not scroll at all")
        void nothingToScroll() {
            var harness = new Harness(new Scroll(new Text("one line")));
            var before = harness.contentTop();

            harness.wheel(3);

            // The overflow is negative and floored at zero, so there is nowhere
            // to go -- rather than the content sliding up out of sight.
            assertEquals(before, harness.contentTop(), 0.01);
        }

        @Test
        @DisplayName("a vertical scroll view ignores a horizontal wheel")
        void wrongAxis() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.router.pointerWheel(100, 50, 3, 0, Modifiers.NONE);
            harness.frame();

            assertEquals(before, harness.contentTop(), 0.01);
        }
    }

    @Nested
    @DisplayName("chaining at the edge")
    class Chaining {

        @Test
        @DisplayName("a wheel that moved something is consumed")
        void consumesWhenItMoves() {
            var harness = new Harness(tallContent());

            assertTrue(harness.router.pointerWheel(100, 50, 0, 1),
                    "a scroll view that moved did not consume the wheel");
        }

        @Test
        @DisplayName("a wheel at the edge is left for an ancestor")
        void releasesAtTheEdge() {
            var harness = new Harness(tallContent());

            // Already at the top, so there is nothing this viewport can do with
            // an upward scroll -- and §2.4 says it chains rather than swallowing
            // it. Unconsumed is the whole mechanism: the router's ordinary
            // bubble does the rest.
            assertFalse(harness.router.pointerWheel(100, 50, 0, -1),
                    "a scroll view at its edge swallowed the wheel");
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("a viewport takes focus, because §1 says its keys work when focused")
        void focusable() {
            var harness = new Harness(tallContent());

            harness.router.focus(harness.viewport(), true);

            assertEquals(harness.viewport(), harness.router.focused());
        }

        @Test
        @DisplayName("PageDown moves a viewport less an overlap")
        void pageDown() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.press(Key.PAGE_DOWN);

            // Not a whole viewport: a page that moved 100 of a 100-tall window
            // would leave nothing on screen that was there before, and a reader
            // could not tell whether they had missed a line.
            assertEquals(before - (VIEWPORT_HEIGHT - ScrollViewport.PAGE_OVERLAP),
                    harness.contentTop(), 0.5);
        }

        @Test
        @DisplayName("End goes to the bottom in one press, and Home comes back")
        void homeAndEnd() {
            var harness = new Harness(tallContent());
            var top = harness.contentTop();

            harness.press(Key.END);
            var bottom = harness.contentTop();
            assertTrue(bottom < top - 100, "End did not reach the end; it moved "
                    + (top - bottom));

            harness.press(Key.HOME);
            assertEquals(top, harness.contentTop(), 0.01);
        }

        @Test
        @DisplayName("Down moves one line, which is what an arrow means everywhere else")
        void arrow() {
            var harness = new Harness(tallContent());
            var before = harness.contentTop();

            harness.press(Key.DOWN);

            assertEquals(before - ScrollViewport.ARROW, harness.contentTop(), 0.5);
        }

        @Test
        @DisplayName("a key that cannot move anything is left for an ancestor")
        void unhandledKeyChains() {
            var harness = new Harness(tallContent());
            harness.router.focus(harness.viewport(), true);

            // At the top already, so Up has nowhere to go -- and a focus scope
            // above this one should still get its turn.
            assertFalse(harness.router.keyPressed(Key.UP, Modifiers.NONE, false),
                    "a scroll view at its edge swallowed the key");
        }
    }

    @Nested
    @DisplayName("the position survives a rebuild")
    class Retained {

        @Test
        @DisplayName("scrolling, then rebuilding, leaves it where it was")
        void survivesRebuild() {
            var harness = new Harness(tallContent());
            harness.wheel(3);
            var scrolled = harness.contentTop();

            // §1: "scroll position is retained state surviving rebuilds". The
            // element tree keeps the state across a re-description, so this
            // needs no key and no application field.
            harness.tree.root().markNeedsBuild();
            harness.frame();

            assertEquals(scrolled, harness.contentTop(), 0.01);
        }
    }

    @Nested
    @DisplayName("the catalog")
    class Catalog {

        @Test
        @DisplayName("scroll is a registered primitive")
        void registered() {
            assertTrue(io.github.digitalsmile.goldberry.widgets.core.Primitives.builtInTypes()
                    .contains("scroll"));
        }

        @Test
        @DisplayName("an axis= attribute reaches the widget, and a misspelling does not throw")
        void axisParses() {
            assertEquals(ScrollAxis.HORIZONTAL, ScrollAxis.parse("horizontal"));
            assertEquals(ScrollAxis.BOTH, ScrollAxis.parse("both"));
            // A document that misspells an attribute should still show its
            // content, which is the registry's rule everywhere else.
            assertEquals(ScrollAxis.VERTICAL, ScrollAxis.parse("sideways"));
            assertEquals(ScrollAxis.VERTICAL, ScrollAxis.parse(null));
        }
    }
}
