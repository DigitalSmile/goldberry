package io.github.digitalsmile.goldberry.widgets.panel.masonry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        var kids = new ArrayList<Widget>(cards);
        for (var i = 0; i < cards; i++) {
            kids.add(new Card(List.of(), new Attributes("c" + i, Set.of(), "c" + i)));
        }
        return new Masonry(kids, columns, new Attributes("wall", Set.of(), "wall"));
    }

    private static Stylesheet sizes(List<Integer> heights) {
        var css = new StringBuilder("#wall { width: 300px }\n");
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
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sizes(heights)), TestFont.get());
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

        private final TestFrames.Target target = TestFrames.of(320, 400, 1.0f);
        private final WidgetRenderer renderer;
        private final ElementTree tree;
        private final RenderTree render = RenderTree.create();
        private final PointerRouter router = new PointerRouter();

        Harness(Widget root, List<Integer> heights) {
            renderer = renderer(heights);
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
}
