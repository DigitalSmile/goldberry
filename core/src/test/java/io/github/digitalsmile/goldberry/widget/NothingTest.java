package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// [Widget#nothing()] — the word the element tree did not have ([ADR-0227]).
///
/// Every `build` has to return a widget, so a widget with nothing to show had to
/// describe an empty box: no room of its own, and still a child, so a `column`
/// with a `gap` puts the gap round the thing that vanished. The claims here are
/// the two halves of the fix — that it contributes **no box**, and that it is
/// still an **element**, which is what makes "empty this frame, full the next" a
/// value change rather than a node being destroyed and rebuilt.
class NothingTest {

    /// A node with a box of its own, so there is something to count.
    private record Brick(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Brick(String id) {
            this(Attributes.NONE.id(id));
        }

        @Override
        public String cssType() {
            return "brick";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// A container, so the children being counted have a parent that keeps them.
    private record Wall(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Wall(Widget... kids) {
            this(List.of(kids), Attributes.NONE);
        }

        @Override
        public String cssType() {
            return "wall";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// A widget that shows a brick, or nothing, on a flag its state owns — the
    /// shape `message` has, with the banner taken out.
    private record Maybe(boolean show) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new MaybeState();
        }
    }

    private static final class MaybeState extends State<Maybe> {

        /// Proof that the element survived a frame in which it described
        /// nothing: a fresh state would count from zero again.
        int builds;

        @Override
        public Widget build(BuildContext context) {
            builds++;
            return widget().show() ? new Brick("inside") : Widget.nothing();
        }
    }

    private Font font;
    private RenderTree layout;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
        layout = RenderTree.create();
    }

    @AfterEach
    void tearDown() {
        if (layout != null) {
            layout.close();
        }
        if (font != null) {
            font.close();
        }
    }

    /// How tall a box tree comes out at, laid out for real — the claim about
    /// gaps is Yoga's and only a layout can show it.
    ///
    /// The height is left **undefined**, which is ADR-0104's rule: a definite
    /// dimension is filled by a root, so measuring against 400 would answer 400
    /// however many children there were.
    private double heightOf(Box box) {
        return layout.measure(box, DisplayScale.ONE, 200, Float.NaN).height();
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "wall { width: 100px } brick { height: 10px }")),
                font);
    }

    /// How many boxes a tree produced, counted over the whole box tree.
    private static int boxes(Box root) {
        var total = 1;
        for (var child : root.children()) {
            total += boxes(child);
        }
        return total;
    }

    @Nested
    @DisplayName("it produces no box")
    class NoBox {

        @Test
        @DisplayName("a wall of two bricks and a nothing draws two bricks")
        void contributesNothing() {
            var withNothing =
                    renderer().render(new ElementTree(new Wall(new Brick("a"), Widget.nothing(), new Brick("b"))));
            var without = renderer().render(new ElementTree(new Wall(new Brick("a"), new Brick("b"))));

            assertEquals(boxes(without), boxes(withNothing), "the nothing produced a box after all");
            assertEquals(3, boxes(withNothing), "the wall and its two bricks");
        }

        /// The whole reason an empty box would not do. Yoga puts a `gap` between
        /// every pair of children it is given, so an empty box in the middle is a
        /// second gap — the hole a dismissed banner used to leave.
        @Test
        @DisplayName("it takes no gap, where an empty box would take one")
        void takesNoGap() {
            var gapped = new WidgetRenderer(
                    List.of(Stylesheet.parse(
                            CascadeLayer.APPLICATION,
                            "wall { width: 100px; flex-direction: column; gap: 20px } brick { height: 10px }")),
                    font);
            var withNothing =
                    gapped.render(new ElementTree(new Wall(new Brick("a"), Widget.nothing(), new Brick("b"))));
            var withEmpty = gapped.render(new ElementTree(new Wall(new Brick("a"), new Wall(), new Brick("b"))));

            // Laid out rather than merely built: the claim is about Yoga, and
            // only a layout can show it.
            //
            // 10 + 20 + 0 + 20 + 10 — an empty box is a third child, so the wall
            // spends a gap on each side of a node with nothing in it. That is the
            // hole a dismissed banner used to leave.
            assertEquals(60.0, heightOf(withEmpty), 0.5, "an empty box should cost two gaps");
            // 10 + 20 + 10 — two children and one gap.
            assertEquals(
                    40.0,
                    heightOf(withNothing),
                    0.5,
                    "a nothing cost a gap, so a dismissed banner still leaves its hole");
        }

        /// A tree whose *root* describes nothing has nothing to paint at all,
        /// which is a mistake rather than a blank window — the same answer the
        /// renderer already gives a root that describes only composition.
        @Test
        @DisplayName("a root that describes only nothing is refused")
        void rootIsRefused() {
            var renderer = renderer();
            var tree = new ElementTree(new Maybe(false));

            assertThrows(IllegalStateException.class, () -> renderer.render(tree));
        }
    }

    @Nested
    @DisplayName("it is still an element")
    class StillAnElement {

        @Test
        @DisplayName("emptying and filling again is one node, not two")
        void keepsItsState() {
            var tree = new ElementTree(new Wall(new Maybe(true)));
            var state = (MaybeState) onlyMaybe(tree).state().orElseThrow();
            assertEquals(1, state.builds);

            tree.root().update(new Wall(new Maybe(false)));
            tree.flush();
            assertSame(state, onlyMaybe(tree).state().orElseThrow(), "describing nothing threw the element away");
            assertEquals(2, state.builds);

            tree.root().update(new Wall(new Maybe(true)));
            tree.flush();
            assertSame(state, onlyMaybe(tree).state().orElseThrow());
            assertEquals(3, state.builds, "a fresh state would have started counting again");
        }

        @Test
        @DisplayName("the nothing has an element of its own under the widget that described it")
        void hasAnElement() {
            var tree = new ElementTree(new Wall(new Maybe(false)));
            tree.flush();

            var maybe = onlyMaybe(tree);
            assertEquals(1, maybe.children().size(), "a described widget always gets an element");
            assertSame(Widget.nothing(), maybe.children().getFirst().widget());
        }

        /// A singleton, so the reconciler matches one against the next by type
        /// and has nothing to tell apart.
        @Test
        @DisplayName("there is one of them")
        void singleton() {
            assertSame(Widget.nothing(), Widget.nothing());
            assertTrue(Widget.nothing() instanceof Widget.Leaf leaf
                    && leaf.children().isEmpty());
        }
    }

    private static Element onlyMaybe(ElementTree tree) {
        return find(tree.root());
    }

    private static Element find(Element from) {
        if (from.widget() instanceof Maybe) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
