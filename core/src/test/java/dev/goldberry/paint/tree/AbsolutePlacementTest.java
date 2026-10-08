package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Decoration;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.model.LogicalRect;

/// Where an absolutely positioned child actually lands: against its parent's
/// padding box, inside the border and outside the padding, as CSS places it.
///
/// `ContainingBlockTest` asserts the arithmetic; this asserts that Yoga does what
/// the arithmetic was aiming at, against the compiled library. Yoga measures an
/// inset from the node's outer edge, which is the padding box for every box
/// without a border; the toolkit's border is painted inside the box and never
/// reaches Yoga, which is the one case the toolkit corrects.
class AbsolutePlacementTest {

    private static final int PARENT = 0xFF2E3440;
    private static final int CHILD = 0xFFA3BE8C;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    private static Length px(float value) {
        return Length.points(value);
    }

    /// A 200×100 block with `padding: 12px`, holding one 40×20 child.
    private static Box block(Box child) {
        return Box.filled(PARENT).size(px(200), px(100)).padding(px(12)).children(child);
    }

    private static Box child() {
        return Box.filled(CHILD).size(px(40), px(20));
    }

    /// Every box's **absolute** rectangle, in walk order — the root first.
    private static List<LogicalRect> layouts(RenderTree tree) {
        var out = new ArrayList<LogicalRect>();
        tree.forEachPlacedBox(placed -> out.add(placed.layout()));
        return out;
    }

    private List<LogicalRect> laidOut(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            return layouts(tree);
        }
    }

    @Nested
    @DisplayName("against the padding box, which is what CSS says")
    class PaddingBox {

        /// CSS's padding box is bounded by the outer edge of the padding, so in a
        /// block with padding and no border `left: 0; top: 0` is the block's own
        /// corner, not its content's.
        @Test
        @DisplayName("left: 0; top: 0 inside 12px of padding lands at (0, 0)")
        void zeroInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), Length.UNDEFINED, Length.UNDEFINED, px(0)))))
                    .get(1);

            assertEquals(LogicalRect.of(0f, 0f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("a non-zero inset is measured from the padding edge too")
        void offsetInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(5), Length.UNDEFINED, Length.UNDEFINED, px(30)))))
                    .get(1);

            assertEquals(LogicalRect.of(30f, 5f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("right: 0; bottom: 0 stops at the far padding edge")
        void trailingInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(Length.UNDEFINED, px(0), px(0), Length.UNDEFINED))))
                    .get(1);

            assertEquals(LogicalRect.of(160f, 80f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("left and right together size the child to the padding box")
        void stretchedAcross() {
            var placed = laidOut(block(Box.filled(CHILD)
                            .size(Length.UNDEFINED, px(20))
                            .position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), px(0), Length.UNDEFINED, px(0)))))
                    .get(1);

            assertEquals(LogicalRect.of(0f, 0f, 200f, 20f), placed);
        }
    }

    @Nested
    @DisplayName("inside the border, which the toolkit paints over the padding")
    class InsideTheBorder {

        /// The case that found the old rule wrong, as it was reported: a
        /// 300×200 block with `padding: 20px 40px` and a 2px border, and a 30×30
        /// pin at `top: 0; right: 0`. CSS puts it on the border's inner edge.
        @Test
        @DisplayName("top: 0; right: 0 in a bordered, padded block lands inside the border's corner")
        void pinInTheCorner() {
            var root = Box.filled(PARENT)
                    .size(px(300), px(200))
                    .padding(new Insets(px(20), px(40), px(20), px(40)))
                    .decoration(Decoration.NONE.border(2, CHILD))
                    .children(Box.filled(CHILD)
                            .size(px(30), px(30))
                            .position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), px(0), Length.UNDEFINED, Length.UNDEFINED)));

            assertEquals(LogicalRect.of(268f, 2f, 30f, 30f), laidOut(root).get(1));
        }

        @Test
        @DisplayName("and filling the block stops at the border on every side")
        void fillStopsAtTheBorder() {
            var root = Box.filled(PARENT)
                    .size(px(200), px(100))
                    .padding(px(12))
                    .decoration(Decoration.NONE.border(3, CHILD))
                    .children(Box.filled(CHILD).position(Position.ABSOLUTE).inset(Insets.all(px(0))));

            assertEquals(LogicalRect.of(3f, 3f, 194f, 94f), laidOut(root).get(1));
        }
    }

    @Nested
    @DisplayName("what the shift must not touch")
    class Untouched {

        /// An edge with no inset takes the static position, where a child in
        /// flow would start: inside the padding. Yoga already does that, and
        /// nothing here defines the edge in order to correct it.
        @Test
        @DisplayName("no insets at all lands where flow would start, inside the padding")
        void noInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE))).get(1);

            assertEquals(LogicalRect.of(12f, 12f, 40f, 20f), placed);
        }

        /// Flow put the child inside the padding already, and a relative inset
        /// offsets it from there.
        @Test
        @DisplayName("a relative inset offsets from the flow position")
        void relativeIsUnshifted() {
            var placed = laidOut(block(child().position(Position.RELATIVE)
                            .inset(new Insets(px(5), Length.UNDEFINED, Length.UNDEFINED, px(5)))))
                    .get(1);

            assertEquals(LogicalRect.of(17f, 17f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("an unpadded, unbordered block places its child where it always did")
        void noPadding() {
            var root = Box.filled(PARENT)
                    .size(px(200), px(100))
                    .children(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(15), Length.UNDEFINED, Length.UNDEFINED, px(30))));

            assertEquals(LogicalRect.of(30f, 15f, 40f, 20f), laidOut(root).get(1));
        }
    }

    @Nested
    @DisplayName("a child that moved because its parent did not")
    class Damage {

        private Box bordered(float border) {
            return Box.filled(PARENT)
                    .size(px(200), px(200))
                    .decoration(Decoration.NONE.border(border, CHILD))
                    .children(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), Length.UNDEFINED, Length.UNDEFINED, px(0))));
        }

        private static boolean covers(List<DamageRect> damage, int x, int y) {
            return damage.stream()
                    .anyMatch(r -> x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height());
        }

        /// The case the rule could plausibly have broken, which is why it is
        /// asserted rather than reasoned about. A child shifted by its containing
        /// block's border has an **identical box** when only the parent's border
        /// changes, so every comparison in `sameAppearance` says nothing happened
        /// about the child — and the two things that make it come out right are
        /// both indirect: the parent's own decoration *is* compared, and damage
        /// is collected by comparing where a node was against where it is rather
        /// than by asking why it moved.
        @Test
        @DisplayName("a parent that grows a border damages where its absolute child was")
        void parentBorderMovesTheChild() {
            try (var render = RenderTree.create()) {
                render.update(target.frame(), bordered(0));
                render.damage(target.frame());

                render.update(target.frame(), bordered(40));
                var damage = render.damage(target.frame());

                assertFalse(damage.isEmpty(), "the child moved from (0, 0) to (40, 40) and nothing was damaged");
                assertTrue(covers(damage, 5, 5), "the rectangle the child left is still showing the old drawing");
                assertTrue(covers(damage, 45, 45), "the rectangle the child arrived in was never redrawn");
            }
        }

        @Test
        @DisplayName("and a parent whose border did not change damages nothing")
        void steadyBorderIsStillFree() {
            try (var render = RenderTree.create()) {
                render.update(target.frame(), bordered(12));
                render.damage(target.frame());

                render.update(target.frame(), bordered(12));

                assertEquals(List.of(), render.damage(target.frame()));
            }
        }
    }
}
