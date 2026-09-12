package io.github.digitalsmile.goldberry.paint.tree;

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

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// Where an absolutely positioned child actually lands — ADR-0272.
///
/// `ContainingBlockTest` asserts the arithmetic; this asserts that Yoga does what
/// the arithmetic was aiming at, against the compiled library. The two Yoga
/// tests that recorded the disagreement live in `:natives`
/// (`YogaLayoutTest.absolutePositioningInsidePadding`) and are deliberately left
/// asserting Yoga's raw answer: they are about the library, and these are about
/// the toolkit built on it. If Yoga ever fixes its inset path, those fail first
/// and these say what to do about it.
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

        /// The exact case ADR-0265 measured: Yoga answers (0, 0) for this and CSS
        /// answers (12, 12). The toolkit now answers (12, 12).
        @Test
        @DisplayName("left: 0; top: 0 inside 12px of padding lands at (12, 12)")
        void zeroInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), Length.UNDEFINED, Length.UNDEFINED, px(0)))))
                    .get(1);

            assertEquals(LogicalRect.of(12f, 12f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("a non-zero inset is measured from the padding edge too")
        void offsetInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(5), Length.UNDEFINED, Length.UNDEFINED, px(30)))))
                    .get(1);

            assertEquals(LogicalRect.of(42f, 17f, 40f, 20f), placed);
        }

        /// The trailing edge, which is the half nothing in the toolkit writes and
        /// which would have been wrong in the other direction: `right: 0` has to
        /// stop at the padding edge, not at the border.
        @Test
        @DisplayName("right: 0; bottom: 0 stops at the far padding edge")
        void trailingInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE)
                            .inset(new Insets(Length.UNDEFINED, px(0), px(0), Length.UNDEFINED))))
                    .get(1);

            assertEquals(LogicalRect.of(148f, 68f, 40f, 20f), placed);
        }

        /// Why the shift is applied to the style rather than to the answer: with
        /// both edges given, the child's *width* is derived from them, and a
        /// correction made after the layout pass could have moved it and not
        /// resized it. 200 − 12 − 12 = 176.
        @Test
        @DisplayName("left and right together size the child to the padding box")
        void stretchedAcross() {
            var placed = laidOut(block(Box.filled(CHILD)
                            .size(Length.UNDEFINED, px(20))
                            .position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), px(0), Length.UNDEFINED, px(0)))))
                    .get(1);

            assertEquals(LogicalRect.of(12f, 12f, 176f, 20f), placed);
        }
    }

    @Nested
    @DisplayName("what the shift must not touch")
    class Untouched {

        /// The other half of Yoga's contradiction, and the reason an undefined
        /// edge is left undefined rather than defined at the padding: this path
        /// was already right, and it still is.
        @Test
        @DisplayName("no insets at all still lands at the padding edge")
        void noInsets() {
            var placed = laidOut(block(child().position(Position.ABSOLUTE))).get(1);

            assertEquals(LogicalRect.of(12f, 12f, 40f, 20f), placed);
        }

        /// Flow put the child inside the padding already. A relative inset offsets
        /// it from there, so shifting it would count the padding twice — which is
        /// exactly the bug removing `text-input`'s compensation avoids.
        @Test
        @DisplayName("a relative inset offsets from the flow position, not from the padding twice")
        void relativeIsUnshifted() {
            var placed = laidOut(block(child().position(Position.RELATIVE)
                            .inset(new Insets(px(5), Length.UNDEFINED, Length.UNDEFINED, px(5)))))
                    .get(1);

            assertEquals(LogicalRect.of(17f, 17f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("an unpadded block places its child where it always did")
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

        private Box padded(float padding) {
            return Box.filled(PARENT)
                    .size(px(200), px(200))
                    .padding(px(padding))
                    .children(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), Length.UNDEFINED, Length.UNDEFINED, px(0))));
        }

        private static boolean covers(List<DamageRect> damage, int x, int y) {
            return damage.stream()
                    .anyMatch(r -> x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height());
        }

        /// The case the fix could plausibly have broken, which is why it is
        /// asserted rather than reasoned about. A child shifted by its containing
        /// block's padding has an **identical box** when only the parent's
        /// padding changes, so every comparison in `sameAppearance` says nothing
        /// happened about the child — and the two things that make it come out
        /// right are both indirect: the parent's own padding *is* compared, and
        /// damage is collected by comparing where a node was against where it is
        /// rather than by asking why it moved. No flag was added for this; this
        /// is what says none was needed.
        @Test
        @DisplayName("a parent that grows padding damages where its absolute child was")
        void parentPaddingMovesTheChild() {
            try (var render = RenderTree.create()) {
                render.update(target.frame(), padded(0));
                render.damage(target.frame());

                render.update(target.frame(), padded(40));
                var damage = render.damage(target.frame());

                assertFalse(damage.isEmpty(), "the child moved from (0, 0) to (40, 40) and nothing was damaged");
                assertTrue(covers(damage, 5, 5), "the rectangle the child left is still showing the old drawing");
                assertTrue(covers(damage, 45, 45), "the rectangle the child arrived in was never redrawn");
            }
        }

        @Test
        @DisplayName("and a parent whose padding did not change damages nothing")
        void steadyPaddingIsStillFree() {
            try (var render = RenderTree.create()) {
                render.update(target.frame(), padded(12));
                render.damage(target.frame());

                render.update(target.frame(), padded(12));

                assertEquals(List.of(), render.damage(target.frame()));
            }
        }
    }
}
