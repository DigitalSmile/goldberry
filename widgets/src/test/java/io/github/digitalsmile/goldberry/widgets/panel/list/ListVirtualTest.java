package io.github.digitalsmile.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;

/// §10's virtualization — a `list` that builds only the rows its viewport can
/// see ([ADR-0213]).
///
/// Like `affix`'s tests, every one of these needs a **painted frame**: the window
/// is computed from where the list was painted against what clips it, and neither
/// rectangle exists until Yoga has run and the router has captured the result.
class ListVirtualTest {

    /// Tall enough for eight rows of 32, so a window is smaller than the model
    /// and larger than one row.
    private static final int VIEWPORT_HEIGHT = 256;
    private static final double ROW_HEIGHT = 32;

    /// Ten thousand, which is the number §10 says v1 cannot do and this can.
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

    private static List<String> names(int count) {
        var out = new ArrayList<String>(count);
        for (var i = 0; i < count; i++) {
            out.add("Row " + i);
        }
        return List.copyOf(out);
    }

    /// The scene: a virtual list of `count` rows inside a scroll view the size of
    /// the window.
    private static final String SCENE = """
            list { width: 200px }
            """;

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();
        private final TestHost host;

        Harness(Widget root) {
            this(root, new TestHost());
        }

        Harness(Widget root, TestHost withHost) {
            host = withHost;
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            Theme.NORD_DARK.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                    TestFont.get());
            tree = new ElementTree(root, host);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            frame();
            // A second frame, because the first is what *produces* the geometry
            // the window is computed from -- exactly as a real window's second
            // frame is.
            frame();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        void wheel(float lines) {
            router.pointerWheel(100, VIEWPORT_HEIGHT / 2f, 0, lines, Modifiers.NONE);
            frame();
            // The move, then the frame that measures where it landed, then the
            // frame that draws the window the second one asked for.
            frame();
            frame();
        }

        /// The rows actually built, by their focus names.
        List<String> rows() {
            var out = new ArrayList<String>();
            collect(tree.root(), "list-row", element -> out.add(element.id()));
            return out;
        }

        int spacers() {
            var out = new ArrayList<Element>();
            collect(tree.root(), "list-spacer", out::add);
            return out.size();
        }

        TestHost host() {
            return host;
        }

        void tick() {
            host.tick();
            frame();
        }
    }

    private static void collect(Element from, String type, java.util.function.Consumer<Element> to) {
        if (type.equals(from.type())) {
            to.accept(from);
        }
        for (var child : from.children()) {
            collect(child, type, to);
        }
    }

    private static Widget scrolled(ListView<String> list) {
        return new Scroll(
                List.of(list), ScrollAxis.VERTICAL, io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
    }

    private static ListView<String> virtualList(int count) {
        return ListView.of(names(count)).virtualized(ROW_HEIGHT).id("rows");
    }

    @Nested
    @DisplayName("the window")
    class Window {

        @Test
        @DisplayName("ten thousand rows build a screenful, not ten thousand")
        void onlyAScreenful() {
            var harness = new Harness(scrolled(virtualList(COUNT)));

            var built = harness.rows().size();
            // Eight rows fit; the overscan adds four each way and the arithmetic
            // rounds up. What matters is the order of magnitude: a list that
            // built its model would be here with 10,000.
            assertTrue(built > 0 && built < 40, () -> "expected a screenful of rows, built " + built);
        }

        @Test
        @DisplayName("it starts at the top, so the first row is built and the last is not")
        void startsAtTheTop() {
            var harness = new Harness(scrolled(virtualList(COUNT)));

            assertTrue(harness.rows().contains("rows-Row 0"), "the first row was not built");
            assertTrue(
                    !harness.rows().contains("rows-Row 9999"),
                    "the last row of ten thousand was built at the top of the list");
        }

        @Test
        @DisplayName("scrolling moves it, so rows that were not built become built")
        void scrollingMovesTheWindow() {
            var harness = new Harness(scrolled(virtualList(COUNT)));
            var atTop = harness.rows();

            harness.wheel(40);
            var afterScroll = harness.rows();

            assertTrue(!afterScroll.equals(atTop), "the window did not move: still " + afterScroll.getFirst());
            assertTrue(!afterScroll.contains("rows-Row 0"), "row 0 is still built after scrolling well past it");
        }

        @Test
        @DisplayName("both spacers are there in the middle, and stand the rest off")
        void twoSpacers() {
            var harness = new Harness(scrolled(virtualList(COUNT)));
            harness.wheel(40);

            assertEquals(2, harness.spacers(), "a window in the middle of a model needs a spacer on each side");
        }

        @Test
        @DisplayName("the top of the model has no spacer above it")
        void oneSpacerAtTheTop() {
            var harness = new Harness(scrolled(virtualList(COUNT)));

            assertEquals(1, harness.spacers(), "a window at the top of a model needs only the spacer below it");
        }

        @Test
        @DisplayName("it settles — a frame that changes nothing asks for no new window")
        void itTerminates() {
            // The rule that makes ADR-0119's facility safe to use this way: the
            // spacers absorb every row the window leaves out, so the total height
            // is a function of the model and the node that was measured does not
            // move. A list that shortened itself would be told a new position,
            // rebuild, and oscillate at the frame rate.
            var harness = new Harness(scrolled(virtualList(COUNT)));
            harness.wheel(40);
            var settled = harness.rows();

            harness.frame();
            harness.frame();
            assertEquals(settled, harness.rows(), "the window kept moving with nothing to move it");
        }
    }

    @Nested
    @DisplayName("a model smaller than the viewport")
    class ShorterThanTheWindow {

        @Test
        @DisplayName("builds every row, and needs no spacer at all")
        void everythingFits() {
            var harness = new Harness(scrolled(virtualList(3)));

            assertEquals(3, harness.rows().size());
            assertEquals(0, harness.spacers());
        }

        @Test
        @DisplayName("an empty model is an empty list rather than a division by nothing")
        void empty() {
            var harness = new Harness(scrolled(
                    ListView.of(List.<String>of()).virtualized(ROW_HEIGHT).id("rows")));

            assertEquals(List.of(), harness.rows());
        }
    }

    @Nested
    @DisplayName("the keyboard still reaches rows that are not built")
    class Reaching {

        @Test
        @DisplayName("End builds the last row and then focuses it")
        void endReachesTheLastRow() {
            // The one thing virtualization breaks and has to put back: `End`
            // moves the focus by *name*, and a name resolves against the element
            // tree -- so the row has to exist before it can be focused.
            var harness = new Harness(scrolled(virtualList(COUNT)));
            var first = harness.rows().getFirst();

            press(harness, first, io.github.digitalsmile.goldberry.input.key.Key.END);
            harness.frame();

            assertTrue(harness.rows().contains("rows-Row 9999"), "End did not build the row it was reaching for");

            // And then focuses it, on the frame loop's own timer -- the rebuild
            // has to have run first.
            harness.tick();
            assertEquals(List.of("rows-Row 9999"), harness.host().focusRequests());
        }

        @Test
        @DisplayName("Home comes back to the first row the same way")
        void homeReachesTheFirstRow() {
            var harness = new Harness(scrolled(virtualList(COUNT)));
            harness.wheel(120);
            harness.host().forgetFocusRequests();

            var somewhere = harness.rows().getFirst();
            press(harness, somewhere, io.github.digitalsmile.goldberry.input.key.Key.HOME);
            harness.frame();
            harness.tick();

            assertEquals(List.of("rows-Row 0"), harness.host().focusRequests());
        }

        @Test
        @DisplayName("a focus that lands before the rebuild is tried again")
        void aRefusedReachIsRetried() {
            // The frame loop fires its timers *after* the platform pump, and
            // whether the repaint a setState asked for was drawn inside that pump
            // depends on the pacer -- so the first attempt may reach a tree that
            // has not been rebuilt yet. `Host.focus` says whether it found
            // anything, which is what makes that recoverable rather than a lost
            // keystroke.
            var refusing = new RefuseOnce();
            var harness = new Harness(scrolled(virtualList(COUNT)), refusing);
            var first = harness.rows().getFirst();

            press(harness, first, io.github.digitalsmile.goldberry.input.key.Key.END);
            harness.frame();

            harness.tick();
            assertEquals(List.of("rows-Row 9999"), refusing.focusRequests(), "the first attempt was not made");

            harness.tick();
            assertEquals(
                    List.of("rows-Row 9999", "rows-Row 9999"),
                    refusing.focusRequests(),
                    "a refused reach was not tried again");
        }

        @Test
        @DisplayName("and it gives up rather than re-arming a timer for ever")
        void aReachThatNeverLandsStops() {
            var refusing = new RefuseAlways();
            var harness = new Harness(scrolled(virtualList(COUNT)), refusing);
            var first = harness.rows().getFirst();

            press(harness, first, io.github.digitalsmile.goldberry.input.key.Key.END);
            harness.frame();

            harness.tick();
            harness.tick();
            harness.tick();
            assertEquals(2, refusing.focusRequests().size(), "a reach that never lands kept scheduling timers");
        }

        @Test
        @DisplayName("a row inside the window is focused without waiting for a frame")
        void aBuiltRowIsFocusedAtOnce() {
            // The cheap path, and the one an arrow key takes: no rebuild, no
            // timer, because the row is already there.
            var harness = new Harness(scrolled(virtualList(COUNT)));
            var first = harness.rows().getFirst();

            press(harness, first, io.github.digitalsmile.goldberry.input.key.Key.HOME);

            assertEquals(List.of("rows-Row 0"), harness.host().focusRequests());
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a negative row height is refused where it is written")
        void negativeHeight() {
            assertThrows(
                    IllegalArgumentException.class, () -> ListView.of(names(3)).virtualized(-1));
        }

        @Test
        @DisplayName("zero is not a refusal — it is how a list says it builds every row")
        void zeroIsOff() {
            assertEquals(0, ListView.of(names(3)).virtualized(0).rowHeight());
        }
    }

    /// A host whose first `focus` finds nothing, as a real one does when the
    /// timer beats the rebuild.
    private static final class RefuseOnce extends TestHost {

        private int calls;

        @Override
        public boolean focus(String id, boolean fromKeyboard) {
            super.focus(id, fromKeyboard);
            return ++calls > 1;
        }
    }

    /// One that never finds it — an id naming no row at all.
    private static final class RefuseAlways extends TestHost {

        @Override
        public boolean focus(String id, boolean fromKeyboard) {
            super.focus(id, fromKeyboard);
            return false;
        }
    }

    private static void press(Harness harness, String rowId, io.github.digitalsmile.goldberry.input.key.Key key) {

        var found = new ArrayList<Element>();
        collect(harness.tree.root(), "list-row", element -> {
            if (rowId.equals(element.id())) {
                found.add(element);
            }
        });
        assertEquals(1, found.size(), () -> "no row " + rowId + " among " + harness.rows());
        ((ListRow) found.getFirst().widget())
                .onKey(new io.github.digitalsmile.goldberry.input.event.KeyEvent(
                        io.github.digitalsmile.goldberry.input.event.KeyEvent.Kind.PRESSED,
                        key,
                        Modifiers.NONE,
                        false,
                        null));
        harness.frame();
    }
}
