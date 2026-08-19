package io.github.digitalsmile.goldberry.widgets.core.affix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `affix` — a child pinned to an edge of the nearest `scroll`
/// ([ADR-0119](../../../../../../../../book/src/adr/0119-a-widget-may-be-told-where-it-is.md)).
///
/// Like `scroll`'s tests, every one of these needs a **painted frame**: the whole
/// widget is a comparison between two rectangles that do not exist until Yoga has
/// run, and the router is the only thing that holds them.
class AffixTest {

    private static final int VIEWPORT_HEIGHT = 120;

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
            target = TestFrames.of(220, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            tree = new ElementTree(root);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 220, VIEWPORT_HEIGHT));
            frame();
            // A second frame, because the first is what *produces* the geometry
            // the affix reacts to -- exactly as a real window's second frame is.
            frame();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        Element affix() {
            return find(tree.root(), "affix");
        }

        /// Where the pinned child is actually drawn, which is the only thing that
        /// matters — the affix's own box never moves by design.
        double contentTop() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && "affix-content".equals(element.type())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    found.add(matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f());
                }
            });
            assertEquals(1, found.size(), "expected exactly one affix-content");
            return found.getFirst();
        }

        /// Where the hole is — the outer node, which must not move at all.
        double holeTop() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && "affix".equals(element.type())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    found.add(matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f());
                }
            });
            return found.getFirst();
        }

        void wheel(float lines) {
            router.pointerWheel(110, 60, 0, lines, Modifiers.NONE);
            frame();
            // The move, then the frame that measures where it landed.
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

    /// A header partway down a tall document, so it starts below the viewport's
    /// top and has somewhere to scroll from.
    private static Widget document() {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < 4; i++) {
            rows.add(new Text("before " + i));
        }
        rows.add(new Affix(List.of(new Text("HEADER")), Edge.TOP, 0, Attributes.NONE));
        for (var i = 0; i < 30; i++) {
            rows.add(new Text("after " + i));
        }
        return new Scroll(List.of(new Column(rows.toArray(Widget[]::new))),
                ScrollAxis.VERTICAL, Attributes.NONE);
    }

    @Nested
    @DisplayName("pinning")
    class Pinning {

        @Test
        @DisplayName("an affix is a hole and a content node")
        void structure() {
            var harness = new Harness(document());

            assertNotNull(harness.affix(), "no element of type affix");
            assertNotNull(find(harness.affix(), "affix-content"),
                    "the affix built no content node");
        }

        @Test
        @DisplayName("it sits where the layout put it until it would scroll past")
        void restsInPlace() {
            var harness = new Harness(document());

            // Nothing has scrolled, so the child is exactly on its hole: an affix
            // that offset itself at rest would push everything below it down.
            assertEquals(harness.holeTop(), harness.contentTop(), 0.5);
            assertFalse(harness.affix().hasState(PseudoClass.AFFIXED));
        }

        @Test
        @DisplayName("it stops at the viewport's top once it would have gone above it")
        void pinsAtTheEdge() {
            var harness = new Harness(document());

            harness.wheel(6);

            // The hole has scrolled up out of sight; the child has not.
            assertTrue(harness.holeTop() < -1,
                    "the hole did not scroll; it is at " + harness.holeTop());
            assertEquals(0, harness.contentTop(), 1.0,
                    "the pinned child left the top of the viewport");
        }

        @Test
        @DisplayName(":affixed comes on the moment it lifts, so a shadow can")
        void pseudoClass() {
            var harness = new Harness(document());
            assertFalse(harness.affix().hasState(PseudoClass.AFFIXED));

            harness.wheel(6);

            assertTrue(harness.affix().hasState(PseudoClass.AFFIXED),
                    ":affixed did not come on when the header lifted");
        }

        @Test
        @DisplayName("it comes back down, and lets go")
        void unpins() {
            var harness = new Harness(document());
            harness.wheel(6);
            assertTrue(harness.affix().hasState(PseudoClass.AFFIXED));

            harness.wheel(-6);

            assertFalse(harness.affix().hasState(PseudoClass.AFFIXED),
                    "it stayed affixed after scrolling back to the top");
            assertEquals(harness.holeTop(), harness.contentTop(), 0.5);
        }

        @Test
        @DisplayName("the hole stays exactly where it was, so nothing below jumps")
        void leavesAHole() {
            var harness = new Harness(document());
            var restingHole = harness.holeTop();

            harness.wheel(6);
            var scrolledHole = harness.holeTop();
            harness.wheel(-6);

            // §1's promise. The hole travels with the document -- it *is* part of
            // the document -- and comes back to precisely where it started, which
            // is what says the affix never took space from it.
            assertTrue(scrolledHole < restingHole, "the hole did not travel");
            assertEquals(restingHole, harness.holeTop(), 0.5);
        }

        @Test
        @DisplayName("it settles rather than chasing itself")
        void terminates() {
            var harness = new Harness(document());
            harness.wheel(6);
            var settled = harness.contentTop();

            // The trap this widget's whole shape exists to avoid: a node that
            // moves in response to being told where it is, is told a new position
            // and moves again. Three frames with no input must change nothing.
            harness.frame();
            harness.frame();
            harness.frame();

            assertEquals(settled, harness.contentTop(), 0.01);
        }

        @Test
        @DisplayName("an offset holds it that far from the edge")
        void offset() {
            var rows = new ArrayList<Widget>();
            for (var i = 0; i < 4; i++) {
                rows.add(new Text("before " + i));
            }
            rows.add(new Affix(List.of(new Text("HEADER")), Edge.TOP, 12, Attributes.NONE));
            for (var i = 0; i < 30; i++) {
                rows.add(new Text("after " + i));
            }
            var harness = new Harness(new Scroll(
                    List.of(new Column(rows.toArray(Widget[]::new))),
                    ScrollAxis.VERTICAL, Attributes.NONE));

            harness.wheel(6);

            assertEquals(12, harness.contentTop(), 1.0);
        }
    }

    @Nested
    @DisplayName("revealing one")
    class Revealing {

        @Test
        @DisplayName("it reports the hole, which travels, and not the content, which pins")
        void reportsTheHole() {
            var seen = new java.util.ArrayList<LogicalRect>();
            var rows = new ArrayList<Widget>();
            for (var i = 0; i < 4; i++) {
                rows.add(new Text("before " + i));
            }
            rows.add(new Affix(List.of(new Text("HEADER")), Edge.TOP, 0, Attributes.NONE)
                    .revealedBy((self, clip) -> seen.add(self)));
            for (var i = 0; i < 30; i++) {
                rows.add(new Text("after " + i));
            }
            var harness = new Harness(new Scroll(
                    List.of(new Column(rows.toArray(Widget[]::new))),
                    ScrollAxis.VERTICAL, Attributes.NONE));

            var atRest = seen.getLast();
            harness.wheel(6);
            var scrolled = seen.getLast();

            // The content is pinned at the viewport's edge by now — which is what
            // the widget is *for*, and what makes it useless to measure. The hole
            // has travelled with the document, and that is what a caller asking
            // "how far away is this section" has to be given.
            assertTrue(scrolled.top() < atRest.top() - 10,
                    "the affix reported a rectangle that did not travel: " + atRest.top()
                            + " then " + scrolled.top());
            assertTrue(harness.affix().hasState(PseudoClass.AFFIXED),
                    "the header was not pinned, so this proves nothing");
        }

        @Test
        @DisplayName("an affix nobody is asking about reports nothing")
        void silentByDefault() {
            // The ordinary case, and it must cost nothing: a list of forty
            // sections has forty affixes and at most one of them is being
            // revealed.
            var harness = new Harness(document());
            harness.wheel(6);

            // Nothing to assert but that it did not throw — `document()` builds
            // its affix without a listener, which is the default constructor.
            assertNotNull(harness.affix());
        }
    }

    @Nested
    @DisplayName("outside a scroll view")
    class Unclipped {

        @Test
        @DisplayName("an affix with nothing above it pins to the window and never lifts")
        void pinsToTheWindow() {
            // §1 defines the widget against "the nearest `scroll`", and the router
            // answers the window when nothing clips -- so this is well-defined
            // rather than a special case, and a toolbar at the top of a page is
            // simply never past the edge.
            var harness = new Harness(new Column(new Affix(new Text("toolbar")),
                    new Text("under it")));

            assertFalse(harness.affix().hasState(PseudoClass.AFFIXED));
            assertEquals(harness.holeTop(), harness.contentTop(), 0.5);
        }
    }

    @Nested
    @DisplayName("the catalog")
    class Catalog {

        @Test
        @DisplayName("affix is a registered primitive")
        void registered() {
            assertTrue(Primitives.builtInTypes().contains("affix"));
        }

        @Test
        @DisplayName("an edge= attribute reaches the widget, and a misspelling does not throw")
        void edgeParses() {
            assertEquals(Edge.BOTTOM, Edge.parse("bottom"));
            assertEquals(Edge.LEFT, Edge.parse("left"));
            assertEquals(Edge.RIGHT, Edge.parse("right"));
            assertEquals(Edge.TOP, Edge.parse("sideways"));
            assertEquals(Edge.TOP, Edge.parse(null));
        }
    }
}
