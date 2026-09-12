package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The cursor, from `cursor: pointer` in a stylesheet to the platform (§7.3).
///
/// Its route is deliberately the same one hit testing takes: the shape is
/// recorded on the box while it is painted and read back off the rectangle under
/// the pointer, because what the cursor should be is a question about what is on
/// screen (ADR-0054).
class CursorTest {

    @Nested
    @DisplayName("the cascade")
    class Cascade {

        private ComputedStyle resolve(String css, Widget widget) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
            var element = new ElementTree(widget).root();
            return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(element), CssLength.Context.DEFAULT);
        }

        @Test
        @DisplayName("`cursor: pointer` resolves to a Cursor, by CSS's own name")
        void keywordByName() {
            assertEquals(
                    Cursor.POINTER,
                    resolve("button { cursor: pointer; }", new Node("button")).cursor());
        }

        @Test
        @DisplayName("a hyphenated keyword maps onto the enum's underscore")
        void hyphenated() {
            // Exactly the rule that already maps `space-between` onto Yoga's
            // SPACE_BETWEEN -- one vocabulary, not two.
            assertEquals(
                    Cursor.EW_RESIZE,
                    resolve("splitter { cursor: ew-resize; }", new Node("splitter"))
                            .cursor());
            assertEquals(
                    Cursor.NOT_ALLOWED,
                    resolve("splitter { cursor: not-allowed; }", new Node("splitter"))
                            .cursor());
        }

        @Test
        @DisplayName("a node with no rule keeps the default arrow")
        void defaults() {
            assertEquals(Cursor.DEFAULT, ComputedStyle.INITIAL.cursor());
            assertEquals(
                    Cursor.DEFAULT,
                    resolve("other { cursor: wait; }", new Node("button")).cursor());
        }

        @Test
        @DisplayName("a cursor nobody has is dropped rather than fatal")
        void unknownKeyword() {
            // Same rule as every other unparseable value: the rest of the node's
            // style is still perfectly good.
            assertEquals(
                    Cursor.DEFAULT,
                    resolve("button { cursor: zoom-in; }", new Node("button")).cursor());
        }

        @Test
        @DisplayName("the cursor reaches the box through Box.style")
        void reachesTheBox() {
            var style = resolve("button { cursor: pointer; }", new Node("button"));
            assertEquals(Cursor.POINTER, Box.of().style(style).cursor());
        }
    }

    @Nested
    @DisplayName("hit testing")
    class Painted {

        @BeforeEach
        void requireRenderer() {
            RendererRequirement.enforce();
        }

        private List<HitTest.Region> capture(Box root) {
            var target = TestFrames.of(100, 100, 1.0f);
            try {
                return HitTest.capture(target.frame(), root);
            } finally {
                target.end();
            }
        }

        @Test
        @DisplayName("the shape is recorded on the rectangle while it is painted")
        void recorded() {
            var root = Box.filled(0xFFFF0000).cursor(Cursor.WAIT).owner("root");

            assertEquals(Cursor.WAIT, capture(root).getFirst().cursor());
        }

        @Test
        @DisplayName("a child with no cursor of its own inherits the one above it")
        void inheritsFromAbove() {
            // The label inside a button should not have to repeat the button's
            // `cursor: pointer`, and CSS's `cursor` is inherited. Walking the
            // stack of rectangles is how that is arrived at here.
            var label = Box.filled(0xFF00FF00)
                    .size(Length.points(40), Length.points(40))
                    .owner("label");
            var button = Box.filled(0xFFFF0000)
                    .cursor(Cursor.POINTER)
                    .padding(Length.points(20))
                    .children(label)
                    .owner("button");

            assertEquals(Cursor.POINTER, HitTest.cursorAt(capture(button), 30, 30));
        }

        @Test
        @DisplayName("a child's own cursor wins over its parent's")
        void childWins() {
            var field = Box.filled(0xFF00FF00)
                    .cursor(Cursor.TEXT)
                    .size(Length.points(40), Length.points(40))
                    .owner("field");
            var panel = Box.filled(0xFFFF0000)
                    .cursor(Cursor.POINTER)
                    .padding(Length.points(20))
                    .children(field)
                    .owner("panel");

            var regions = capture(panel);
            assertEquals(Cursor.TEXT, HitTest.cursorAt(regions, 30, 30));
            assertEquals(Cursor.POINTER, HitTest.cursorAt(regions, 5, 5));
        }

        @Test
        @DisplayName("nowhere in particular is the default arrow")
        void nothingUnderIt() {
            var root = Box.filled(0xFFFF0000)
                    .cursor(Cursor.POINTER)
                    .size(Length.points(20), Length.points(20))
                    .owner("root");

            assertEquals(Cursor.DEFAULT, HitTest.cursorAt(capture(root), 80, 80));
        }
    }

    @Nested
    @DisplayName("routing")
    class Routing {

        private final List<Cursor> seen = new ArrayList<>();
        private PointerRouter router;
        private Element outer;
        private Element inner;

        @BeforeEach
        void buildTree() {
            router = new PointerRouter();
            var innerWidget = new Node("inner");
            var tree = new ElementTree(new Node("outer", innerWidget));
            outer = tree.root();
            inner = outer.children().getFirst();
            router.updateRegions(List.of(
                    new HitTest.Region(outer, Cursor.DEFAULT, 0, 0, 100, 100),
                    new HitTest.Region(inner, Cursor.POINTER, 20, 20, 40, 40)));
            router.onCursorChange(seen::add);
            seen.clear();
        }

        @Test
        @DisplayName("moving onto a widget changes the shape, and moving off changes it back")
        void followsThePointer() {
            router.pointerMoved(30, 30);
            assertEquals(Cursor.POINTER, router.cursor());

            router.pointerMoved(5, 5);
            assertEquals(Cursor.DEFAULT, router.cursor());

            assertEquals(List.of(Cursor.POINTER, Cursor.DEFAULT), seen);
        }

        @Test
        @DisplayName("moving within one widget does not tell the platform again")
        void coalesces() {
            router.pointerMoved(30, 30);
            router.pointerMoved(31, 31);
            router.pointerMoved(40, 40);

            // This is asked for every pixel of a drag, so the notification has to
            // be edge-triggered rather than per event.
            assertEquals(List.of(Cursor.POINTER), seen);
        }

        @Test
        @DisplayName("the shape is frozen during a drag")
        void frozenWhileCaptured() {
            router.pointerMoved(30, 30);
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            seen.clear();

            router.pointerMoved(5, 5);

            // A drag decides what the pointer looks like when it starts; a cursor
            // that flickered across the widgets underneath would be advertising
            // things the user cannot currently touch.
            assertEquals(Cursor.POINTER, router.cursor());
            assertTrue(seen.isEmpty(), () -> "seen was " + seen);

            router.pointerReleased(5, 5, PointerEvent.Button.PRIMARY, 1);
            assertEquals(Cursor.DEFAULT, router.cursor(), "and thaws when the drag ends");
        }

        @Test
        @DisplayName("the pointer leaving the window resets the shape")
        void resetOnExit() {
            router.pointerMoved(30, 30);
            router.pointerExited();

            assertEquals(Cursor.DEFAULT, router.cursor());
        }
    }

    /// [ADR-0237]: the shape follows the *frame*, not only the pointer.
    ///
    /// A widget that becomes disabled under a still pointer resolves
    /// `cursor: not-allowed` in the frame it is painted for, and a router that
    /// only ever recomputed on motion went on advertising the shape the control
    /// used to have — until the user moved, which is exactly what somebody
    /// deciding whether to click does not do.
    @Nested
    @DisplayName("the tree changing under a still pointer")
    class Restill {

        private final List<Cursor> seen = new ArrayList<>();
        private PointerRouter router;
        private Element outer;
        private Element inner;

        /// The same two rectangles [Routing] uses, so the only difference between
        /// the two groups is what moves.
        @BeforeEach
        void buildTree() {
            router = new PointerRouter();
            var tree = new ElementTree(new Node("outer", new Node("inner")));
            outer = tree.root();
            inner = outer.children().getFirst();
            paint(Cursor.POINTER);
            router.onCursorChange(seen::add);
            seen.clear();
        }

        /// One frame, with the inner rectangle drawn under `shape`.
        private void paint(Cursor shape) {
            router.updateRegions(List.of(
                    new HitTest.Region(outer, Cursor.DEFAULT, 0, 0, 100, 100),
                    new HitTest.Region(inner, shape, 20, 20, 40, 40)));
        }

        @Test
        @DisplayName("a control that disables itself under the pointer gets the new shape")
        void repaintRecomputes() {
            router.pointerMoved(30, 30);
            assertEquals(Cursor.POINTER, router.cursor());
            seen.clear();

            // The sequence `mark` already names: "a button commonly disables
            // itself in its own press handler while the pointer is still over
            // it". Nothing moves; the next frame simply paints it disabled.
            paint(Cursor.NOT_ALLOWED);

            assertEquals(Cursor.NOT_ALLOWED, router.cursor(), "the frame repainted and the cursor did not follow");
            assertEquals(List.of(Cursor.NOT_ALLOWED), seen);
        }

        @Test
        @DisplayName("a frame that changed nothing under the pointer tells the platform nothing")
        void unchangedFrameIsSilent() {
            router.pointerMoved(30, 30);
            seen.clear();

            paint(Cursor.POINTER);
            paint(Cursor.POINTER);

            // This now runs once per frame rather than once per motion, so
            // edge-triggering is load-bearing in a way it was not before:
            // `setCursor` is what keeps a 120Hz repaint from being 120 platform
            // calls a second.
            assertTrue(seen.isEmpty(), () -> "seen was " + seen);
        }

        @Test
        @DisplayName("a frame painted before the pointer has ever arrived asks nothing")
        void noPointerYet() {
            paint(Cursor.NOT_ALLOWED);

            // There is no position to recompute against, and `cursorAt` given one
            // would be answering about a point the pointer is not at.
            assertEquals(Cursor.DEFAULT, router.cursor());
            assertTrue(seen.isEmpty(), () -> "seen was " + seen);
        }

        @Test
        @DisplayName("and a frame painted after the pointer has left asks nothing either")
        void afterThePointerLeft() {
            router.pointerMoved(30, 30);
            router.pointerExited();
            seen.clear();

            paint(Cursor.NOT_ALLOWED);

            // The pointer is somewhere else entirely -- another window, or off
            // the screen -- so the last position it had here is not a place to
            // ask about.
            assertEquals(Cursor.DEFAULT, router.cursor());
            assertTrue(seen.isEmpty(), () -> "seen was " + seen);
        }

        /// The other half of the same frame hook, and the one `mark`'s own
        /// comment claimed was already working: "a control that was hovered
        /// before it became disabled does not keep the state". It did keep it —
        /// clearing is not suppressed, but nothing called it, because
        /// `updateHover` returns early when the element under the pointer has not
        /// changed. §2.1 says a disabled control must not light up.
        @Test
        @DisplayName("a control that disables itself under the pointer loses :hover")
        void hoverIsRestated() {
            var button = new Switchable("button");
            var tree = new ElementTree(button);
            var element = tree.root();
            var live = new PointerRouter();
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.POINTER, 0, 0, 100, 100)));
            live.pointerMoved(30, 30);
            assertTrue(element.hasState(Selector.PseudoClass.HOVER), "the pointer never lit the control at all");

            button.disabled = true;
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.NOT_ALLOWED, 0, 0, 100, 100)));

            assertFalse(
                    element.hasState(Selector.PseudoClass.HOVER),
                    "a disabled control kept the wash that says it can be used");
        }

        /// And it comes back, which is what stops the fix being "clear it once".
        @Test
        @DisplayName("and gets it back when it is enabled again under the same still pointer")
        void hoverReturns() {
            var button = new Switchable("button");
            var tree = new ElementTree(button);
            var element = tree.root();
            var live = new PointerRouter();
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.POINTER, 0, 0, 100, 100)));
            live.pointerMoved(30, 30);
            button.disabled = true;
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.NOT_ALLOWED, 0, 0, 100, 100)));

            button.disabled = false;
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.POINTER, 0, 0, 100, 100)));

            assertTrue(element.hasState(Selector.PseudoClass.HOVER), "the control came back and stayed dim");
        }

        /// `:active` is the same field asked of the pressed chain rather than the
        /// hovered one, and a control that disables itself *in its own press
        /// handler* is the sequence that hits both at once.
        @Test
        @DisplayName("and a control disabled mid-press loses :active too")
        void activeIsRestated() {
            var button = new Switchable("button");
            var tree = new ElementTree(button);
            var element = tree.root();
            var live = new PointerRouter();
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.POINTER, 0, 0, 100, 100)));
            live.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            assertTrue(element.hasState(Selector.PseudoClass.ACTIVE), "the press never lit the control at all");

            button.disabled = true;
            live.updateRegions(List.of(new HitTest.Region(element, Cursor.NOT_ALLOWED, 0, 0, 100, 100)));

            assertFalse(
                    element.hasState(Selector.PseudoClass.ACTIVE),
                    "a disabled control kept the darkening that says it is being pressed");
        }

        @Test
        @DisplayName("a repaint during a drag does not thaw the frozen shape")
        void captureStillWins() {
            router.pointerMoved(30, 30);
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            seen.clear();

            paint(Cursor.NOT_ALLOWED);

            // The freeze is `updateCursor`'s own rule and this must not reach
            // around it: a drag decides the shape when it starts, and a list that
            // repaints under a drag would otherwise change it mid-gesture.
            assertEquals(Cursor.POINTER, router.cursor());
            assertTrue(seen.isEmpty(), () -> "seen was " + seen);
        }
    }

    @Nested
    @DisplayName("through the backend")
    class Plumbing {

        private HeadlessBackend backend;

        @BeforeEach
        void install() {
            // The frame loop paints, and painting is Blend2D even on the
            // headless backend — see [PointerPlumbingTest].
            RendererRequirement.enforce();
            backend = new HeadlessBackend(new DisplayScale(1f));
            io.github.digitalsmile.goldberry.GoldberryTestAccess.install(backend);
        }

        @AfterEach
        void shutdown() {
            Goldberry.shutdown();
        }

        @Test
        @Timeout(10)
        @DisplayName("a move over a widget sets the window's cursor")
        void routerReachesTheWindow() {
            var router = new PointerRouter();
            var tree = new ElementTree(new Node("button"));
            router.updateRegions(List.of(new HitTest.Region(tree.root(), Cursor.POINTER, 0, 0, 100, 100)));

            var window = Window.open(WindowSpec.of("cursor", LogicalSize.of(100f, 100f)));
            window.pointerRouter(router);
            var backendWindow = (HeadlessWindow) backend.windows().getFirst();

            backendWindow.movePointer(30, 30);
            backendWindow.requestClose();
            Goldberry.run();

            assertSame(Cursor.POINTER, backendWindow.cursor());
            assertEquals(1, backendWindow.cursorChanges());
        }

        @Test
        @Timeout(10)
        @DisplayName("an application can set the cursor itself, with no router at all")
        void windowSetsItDirectly() {
            var window = Window.open(WindowSpec.of("cursor", LogicalSize.of(100f, 100f)));
            var backendWindow = (HeadlessWindow) backend.windows().getFirst();

            window.cursor(Cursor.CROSSHAIR);

            assertSame(Cursor.CROSSHAIR, backendWindow.cursor());
            window.close();
        }

        @Test
        @Timeout(10)
        @DisplayName("setting the same shape twice does not talk to the platform twice")
        void repeatIsFree() {
            var window = Window.open(WindowSpec.of("cursor", LogicalSize.of(100f, 100f)));
            var backendWindow = (HeadlessWindow) backend.windows().getFirst();

            window.cursor(Cursor.WAIT);
            window.cursor(Cursor.WAIT);
            window.cursor(Cursor.WAIT);

            assertEquals(1, backendWindow.cursorChanges());
            window.close();
        }
    }

    /// A minimal styleable, selectable node.
    /// A [Node] that can be disabled after the tree holding it was built, which
    /// a record cannot be — the whole point is that the *widget* changes under a
    /// pointer that does not.
    private static final class Switchable implements Widget.Leaf, Styled, Handles {
        private final String name;
        private boolean disabled;

        Switchable(String name) {
            this.name = name;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public boolean isDisabled() {
            return disabled;
        }
    }

    private static class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;

        Node(String name, Widget... children) {
            this.name = name;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return name;
        }
    }
}
