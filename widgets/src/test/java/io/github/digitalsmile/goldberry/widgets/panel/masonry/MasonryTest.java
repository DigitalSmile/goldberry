package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;

/// Cards in columns, each under the shortest one.
///
/// The interesting behaviour is **across frames**: the first is round-robin
/// because nothing has been measured yet, and the second is a real masonry. So
/// these render, lay out, and render again — which is what a real frame loop
/// does and what makes the widget honest about being one frame behind.
class MasonryTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Widget wall(int cards, int columns) {
        return new Masonry(cards(cards), columns, wallId());
    }

    private static Stylesheet sizes(List<Integer> heights) {
        return sizes(heights, 300);
    }

    private static Stylesheet sizes(List<Integer> heights, int wall) {
        var css = new StringBuilder("#wall { width: " + wall + "px }\n");
        for (var i = 0; i < heights.size(); i++) {
            css.append("#c")
                    .append(i)
                    .append(" { height: ")
                    .append(heights.get(i))
                    .append("px }\n");
        }
        return Stylesheet.parse(CascadeLayer.APPLICATION, css.toString());
    }

    private static WidgetRenderer renderer(List<Integer> heights) {
        return renderer(heights, 300);
    }

    private static WidgetRenderer renderer(List<Integer> heights, int wall) {
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sizes(heights, wall)), TestFont.get());
    }

    /// Which column each card ended up in, read out of the built box tree.
    private static List<Integer> columnsOf(Box root, int cards) {
        var found = new ArrayList<Integer>();
        for (var i = 0; i < cards; i++) {
            found.add(-1);
        }
        for (var c = 0; c < root.children().size(); c++) {
            for (var cell : root.children().get(c).children()) {
                if (cell.children().isEmpty()) {
                    continue;
                }
                var owner = cell.children().getFirst().owner();
                if (owner instanceof io.github.digitalsmile.goldberry.widget.Element element
                        && element.widget()
                                instanceof io.github.digitalsmile.goldberry.widget.attr.Attributed<?> attributed
                        && attributed.attributes().id() != null) {
                    found.set(Integer.parseInt(attributed.attributes().id().substring(1)), c);
                }
            }
        }
        return found;
    }

    /// One frame, exactly as a window runs it: flush what went dirty, render,
    /// lay out into a retained tree, and hand the router the rectangles.
    ///
    /// The last step is the one that matters here. `Measured` is delivered by the
    /// **router**, from the regions a laid-out frame produced — not by rendering
    /// and not by painting — so a test that only rendered would never bank a
    /// height and a masonry would look like it did not work.
    private static final class Harness implements AutoCloseable {

        private final TestFrames.Target target;
        private final WidgetRenderer renderer;
        private final ElementTree tree;
        private final RenderTree render = RenderTree.create();
        private final PointerRouter router = new PointerRouter();

        Harness(Widget root, List<Integer> heights) {
            this(root, heights, 300);
        }

        /// `#wall`'s width is a parameter because a responsive wall's column
        /// count is a function of it — every other case here is indifferent to it
        /// and takes the 300 they were all written against.
        Harness(Widget root, List<Integer> heights, int wall) {
            target = TestFrames.of(Math.max(wall + 20, 320), 400, 1.0f);
            renderer = renderer(heights, wall);
            tree = new ElementTree(root);
        }

        Box frame() {
            tree.flush();
            var box = renderer.render(tree);
            render.update(target.frame(), box);
            router.updateRegions(HitTest.capture(render));
            return box;
        }

        @Override
        public void close() {
            render.close();
            target.end();
        }
    }

    @Test
    @DisplayName("the first frame fills across, because nothing has been measured")
    void theFirstFrameIsRoundRobin() {
        var heights = List.of(100, 20, 20, 20);
        try (var harness = new Harness(wall(heights.size(), 3), heights)) {
            var first = columnsOf(harness.frame(), heights.size());

            // Every column looks equally empty, so the tiebreak sends each card
            // to the emptiest: 0, 1, 2, then back to 0.
            assertEquals(List.of(0, 1, 2, 0), first);
        }
    }

    @Test
    @DisplayName("the second frame puts a card under the shortest column, not the tallest")
    void theSecondFrameIsAMasonry() {
        // Card 0 is five times the others, so once its height is known the
        // fourth card must not go under it -- which is the whole widget.
        var heights = List.of(100, 20, 20, 20);
        try (var harness = new Harness(wall(heights.size(), 3), heights)) {
            harness.frame();
            var second = columnsOf(harness.frame(), heights.size());

            assertTrue(
                    second.get(3) != 0,
                    "the fourth card should avoid the 100px column, and it went to " + second.get(3));
        }
    }

    @Test
    @DisplayName("it settles, rather than moving a card every frame")
    void itConverges() {
        // The risk in reading last frame is a layout that oscillates: card moves,
        // heights change, card moves back. Column widths are equal, so a card's
        // height does not depend on its column -- which is `Measured`'s third
        // rule satisfied by construction rather than by luck.
        var heights = List.of(100, 20, 20, 20, 60, 30);
        try (var harness = new Harness(wall(heights.size(), 3), heights)) {
            harness.frame();
            var second = columnsOf(harness.frame(), heights.size());
            var third = columnsOf(harness.frame(), heights.size());
            var fourth = columnsOf(harness.frame(), heights.size());

            assertEquals(second, third, "the layout moved on the third frame");
            assertEquals(third, fourth, "and again on the fourth");
        }
    }

    @Test
    @DisplayName("every column is an equal share of the row, whatever it holds")
    void columnsAreEqualWidth() {
        // The property everything else rests on. `flex-grow` alone would size a
        // column to its content, and then a card's height would depend on which
        // column it landed in -- the loop this layout is built to avoid.
        var heights = List.of(100, 20, 20, 20);
        var widths = new ArrayList<Float>();
        try (var harness = new Harness(wall(heights.size(), 3), heights)) {
            var root = harness.frame();
            BoxPainter.forEachBox(harness.target.frame(), root, (box, layout) -> {
                if ("masonry-column".equals(cssTypeOf(box))) {
                    widths.add(layout.width());
                }
            });
        }
        assertEquals(3, widths.size(), "three columns");
        for (var width : widths) {
            assertEquals(widths.getFirst(), width, 0.5f, "the columns came out unequal: " + widths);
        }
    }

    private static String cssTypeOf(Box box) {
        return box.owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                        && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                ? styled.cssType()
                : null;
    }

    @Test
    @DisplayName("one column is a plain column, and zero is refused")
    void columnCountIsChecked() {
        assertEquals(1, new Masonry(List.of(), 1, Attributes.NONE).columns());
        assertThrows(IllegalArgumentException.class, () -> new Masonry(List.of(), 0, Attributes.NONE));
    }

    @Test
    @DisplayName("markup writes one")
    void inflatesFromKdl() {
        var masonry = (Masonry) Widgets.inflater()
                .inflate(KdlParser.parse("masonry columns=4 { card; card }").getFirst());

        assertEquals(4, masonry.columns());
        assertEquals(2, masonry.children().size());
    }

    // --- min-column-width (ADR-0436) -----------------------------------------

    @Nested
    @DisplayName("a wall that counts its own columns")
    class Responsive {

        /// The fence-post, which is the only arithmetic in the widget and the one
        /// place an off-by-one would be invisible: `n` columns need `n` minimums
        /// **and `n - 1` gaps**, so 640 holds two 320s only when there is no gap
        /// between them.
        @Test
        @DisplayName("a column count is minimums plus the gaps between them")
        void theGapsAreCounted() {
            var wall = new Masonry(List.of()).minColumnWidth(320);

            assertEquals(1, wall.columnsAt(640, 12), "two 320s and a 12 gap need 652");
            assertEquals(2, wall.columnsAt(652, 12), "and 652 is exactly enough");
            assertEquals(2, wall.columnsAt(640, 0), "without a gap 640 holds two");
            assertEquals(3, wall.columnsAt(984, 12), "three 320s and two gaps");
        }

        /// One, not zero and not none. A wall narrower than one column still has
        /// to draw its cards somewhere, and the alternative — a wall with no
        /// columns — is a screen that silently loses its content.
        @Test
        @DisplayName("a wall too narrow for one column still has one")
        void neverFewerThanOne() {
            var wall = new Masonry(List.of()).minColumnWidth(320);

            assertEquals(1, wall.columnsAt(10, 12));
            assertEquals(1, wall.columnsAt(0, 12), "and an unmeasured first frame is one column");
            assertEquals(1, wall.columnsAt(Double.NaN, 12));
        }

        /// A fixed count ignores the width entirely, which is what makes the two
        /// modes worth telling apart at all.
        @Test
        @DisplayName("a fixed count is not a function of the width")
        void aFixedCountIgnoresTheWidth() {
            var wall = new Masonry(List.of(), 2, Attributes.NONE);

            assertFalse(wall.responsive());
            assertEquals(2, wall.columnsAt(4000, 12));
            assertEquals(2, wall.columnsAt(0, 12));
        }

        /// §1's rule, and the reason it is a throw rather than a precedence: a
        /// document that said both meant one of them, and guessing which turns a
        /// typo into a layout nobody can explain.
        @Test
        @DisplayName("both at once is refused, in Java and in markup")
        void theTwoAreExclusive() {
            var thrown =
                    assertThrows(IllegalArgumentException.class, () -> new Masonry(List.of(), 3, 320, Attributes.NONE));
            assertTrue(thrown.getMessage().contains("min-column-width"), thrown.getMessage());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("masonry columns=3 min-column-width=320 { card }")
                                    .getFirst()));
        }

        /// §3's row: a wall that names neither is responsive at 320. The default
        /// is a *width* and not a count, which is the whole of ADR-0436.
        @Test
        @DisplayName("a wall that names neither is responsive at 320")
        void theDefaultIsAWidth() {
            var wall = new Masonry(List.of(new Card(List.of(), Attributes.NONE)));

            assertTrue(wall.responsive());
            assertEquals(Masonry.UNSET, wall.columns());
            assertEquals(Masonry.DEFAULT_MIN_COLUMN_WIDTH, wall.minColumnWidth());
            assertEquals(320, Masonry.DEFAULT_MIN_COLUMN_WIDTH);
        }

        @Test
        @DisplayName("markup writes one")
        void inflatesFromKdl() {
            var wall = (Masonry) Widgets.inflater()
                    .inflate(KdlParser.parse("masonry min-column-width=280 { card; card }")
                            .getFirst());

            assertTrue(wall.responsive());
            assertEquals(280, wall.minColumnWidth());
            assertEquals(2, wall.children().size());
        }

        /// The chain has to be able to change its mind, and the two steps cannot
        /// both be set — so each clears the other rather than throwing halfway
        /// through a builder.
        @Test
        @DisplayName("the two builder steps replace each other")
        void theBuilderStepsReplaceEachOther() {
            var responsive = new Masonry(List.of(), 3, Attributes.NONE).minColumnWidth(280);
            var fixed = responsive.columns(4);

            assertEquals(Masonry.UNSET, responsive.columns());
            assertEquals(280, responsive.minColumnWidth());
            assertEquals(4, fixed.columns());
            assertEquals(Masonry.UNSET, fixed.minColumnWidth());
            assertEquals(responsive, new Masonry(List.of()).minColumnWidth(280), "and the step commutes with itself");
        }

        /// The widget, end to end: the first frame has no width and is one
        /// column, and the frame after the router has handed the wall its
        /// rectangle is as many columns as fit in it.
        ///
        /// 652 rather than 640 for [#theGapsAreCounted]'s reason, and `#wall` is
        /// a definite width because that is the arrangement this mode is safe in
        /// — see `MasonrySettleTest`.
        @Test
        @DisplayName("the wall counts its columns from the width it was laid out at")
        void theWidthDecidesTheCount() {
            var heights = List.of(40, 40, 40, 40);
            var wall = new Masonry(cards(heights.size()), Masonry.UNSET, 320, wallId());
            try (var harness = new Harness(wall, heights, 652)) {
                assertEquals(1, columnCount(harness.frame()), "nothing has measured it yet");
                assertEquals(2, columnCount(harness.frame()), "652 holds two 320s and the 12 between them");
            }
        }

        @Test
        @DisplayName("a narrower wall gets fewer columns, and the same cards")
        void aNarrowerWallGetsFewerColumns() {
            var heights = List.of(40, 40, 40, 40);
            try (var harness =
                    new Harness(new Masonry(cards(heights.size()), Masonry.UNSET, 320, wallId()), heights, 500)) {
                harness.frame();
                var second = harness.frame();

                assertEquals(1, columnCount(second), "500 holds one 320 and not two");
                assertEquals(List.of(0, 0, 0, 0), columnsOf(second, heights.size()), "and every card is still in it");
            }
        }
    }

    private static Attributes wallId() {
        return new Attributes("wall", Set.of(), "wall");
    }

    private static List<Widget> cards(int count) {
        var kids = new ArrayList<Widget>(count);
        for (var i = 0; i < count; i++) {
            kids.add(new Card(List.of(), new Attributes("c" + i, Set.of(), "c" + i)));
        }
        return kids;
    }

    /// How many `masonry-column`s the built tree has, which is the count the
    /// state decided on.
    private static int columnCount(Box root) {
        return root.children().size();
    }
}
