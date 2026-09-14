package io.github.digitalsmile.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;

/// What the painter is allowed to skip, and what it is not — [ADR-0313].
///
/// Two kinds of assertion here and both are needed. The **counts** say the walk
/// actually stopped, which no image can: a culler that quietly stopped working
/// draws the same frame four times as slowly. The **pixels** say it stopped in
/// the right place, which no count can.
@DisplayName("culling a subtree that cannot be seen")
class CullingTest {

    /// A 200×200 window with a 100-tall viewport in it, so half of what is
    /// written below is off screen by construction.
    private TestFrames.Target target;

    /// `transform: translateY(points)` — how a box is put somewhere Yoga did not
    /// lay it out, which is the shape every case below needs and the shape a
    /// `scroll` uses for real.
    private static Transform upBy(double points) {
        return Transform.of(new Transform.Function.Translate(Transform.Length.ZERO, Transform.Length.px(points)));
    }

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// A box of `argb` that keeps the size it asked for.
    ///
    /// **`shrink(0)` is the whole of what makes these tests about culling.** A
    /// flex item shrinks by default, so fifty rows of 20 in a 100-tall column are
    /// fifty rows of *two* — a column with nothing off screen at all, which culls
    /// nothing and proves nothing.
    private static Box row(int argb, double height) {
        return Box.filled(argb)
                .size(Length.points(100), Length.points((float) height))
                .shrink(0);
    }

    /// A clipping viewport `height` tall holding `rows` boxes of 20, which is a
    /// scroll view with the scrolling taken out.
    private static Box viewport(double height, int rows) {
        var children = new Box[rows];
        for (var i = 0; i < rows; i++) {
            children[i] = row(GREEN, 20);
        }
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .size(Length.points(100), Length.points((float) height))
                .overflow(Overflow.HIDDEN)
                .children(children);
    }

    @Nested
    @DisplayName("the count")
    class Counts {

        @Test
        @DisplayName("rows past the bottom of a viewport are not drawn and not walked")
        void offscreenRowsAreSkipped() {
            try (var tree = RenderTree.create()) {
                // Five rows of 20 fit in 100; the other forty-five do not.
                tree.update(target.frame(), viewport(100, 50));
                tree.paint(target.frame());

                assertEquals(5 + 1, tree.boxesPainted(), "the viewport and the five rows inside it");
                assertEquals(45, tree.boxesCulled(), "and one skip per row that could not be seen");
            }
        }

        @Test
        @DisplayName("a window with nothing clipping in it culls nothing, and pays nothing to find out")
        void unclippedTreesAreNotTested() {
            try (var tree = RenderTree.create()) {
                var unclipped = Box.of()
                        .direction(FlexDirection.COLUMN)
                        .size(Length.points(100), Length.points(100))
                        .children(row(GREEN, 20), row(GREEN, 20));

                tree.update(target.frame(), unclipped);
                tree.paint(target.frame());

                assertEquals(3, tree.boxesPainted());
                assertEquals(0, tree.boxesCulled(), "nothing clips, so nothing can be ruled out");
            }
        }

        /// The property the icon sheet needed: what a viewport costs to paint
        /// follows how much of it is **on screen**, not how much there is
        /// ([ADR-0313]).
        @Test
        @DisplayName("ten times the rows in the same viewport costs the same to paint")
        void costFollowsWhatIsVisible() {
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), viewport(100, 50));
                tree.paint(target.frame());
                var few = tree.boxesPainted();

                tree.update(target.frame(), viewport(100, 500));
                tree.paint(target.frame());

                assertEquals(few, tree.boxesPainted(), "the same five rows are on screen either way");
            }
        }
    }

    @Nested
    @DisplayName("what it must never skip")
    class Keeps {

        /// The row straddling the bottom edge. One pixel of it is inside, and a
        /// culler comparing the wrong way round loses the whole row.
        @Test
        @DisplayName("a row half over the edge is drawn, and the row after it is not")
        void theStraddlingRow() {
            try (var tree = RenderTree.create()) {
                // 90 tall: four whole rows, and half of the fifth.
                tree.update(target.frame(), viewport(90, 50));
                tree.paint(target.frame());

                assertEquals(5 + 1, tree.boxesPainted(), "four whole rows and the one being cut in half");
                assertEquals(GREEN, target.pixel(50, 89), "the last visible line of it is drawn");
                assertEquals(0, target.alphaAt(50, 91), "and nothing past the viewport is");
            }
        }

        /// A child is a node of its own and contributes its own ink, so a box
        /// hanging out of a parent that is itself off screen keeps the subtree
        /// alive — which is what culling on the parent's rectangle alone would
        /// get wrong.
        @Test
        @DisplayName("a child that overflows an off-screen parent is still drawn")
        void aChildOutsideItsParent() {
            try (var tree = RenderTree.create()) {
                var escaping = Box.of()
                        .direction(FlexDirection.COLUMN)
                        .size(Length.points(100), Length.points(100))
                        .overflow(Overflow.HIDDEN)
                        .children(
                                // A spacer that fills the viewport, and then a box
                                // below it -- off screen where Yoga put it, and
                                // dragged back into view by a transform.
                                row(0x00000000, 100), row(RED, 20).transform(upBy(-60)));

                tree.update(target.frame(), escaping);
                tree.paint(target.frame());

                assertEquals(RED, target.pixel(50, 50), "the transform brought it back inside the viewport");
            }
        }

        /// A focus ring is drawn outside the border box by design, so a control
        /// whose *rectangle* is one pixel above a viewport still has 4px of ring
        /// inside it.
        @Test
        @DisplayName("a focus ring hanging into the viewport keeps its box alive")
        void aRingReachingIn() {
            try (var tree = RenderTree.create()) {
                var ringed = Box.of()
                        .direction(FlexDirection.COLUMN)
                        .size(Length.points(100), Length.points(100))
                        .overflow(Overflow.HIDDEN)
                        .children(row(0x00000000, 20)
                                .decoration(new Decoration(Corners.SQUARE, 0, 0, 2, RED, 2, Shadow.NONE))
                                .transform(upBy(-23)));

                tree.update(target.frame(), ringed);
                tree.paint(target.frame());

                assertEquals(0, tree.boxesCulled(), "the box is above the viewport and its ring is not");
                assertTrue(target.alphaAt(50, 0) > 0, "the bottom of the ring is inside and is drawn");
            }
        }

        /// The same argument for the other thing drawn outside a box, and the one
        /// that reaches furthest: a shadow's blur (ADR-0310).
        @Test
        @DisplayName("a drop shadow reaching into the viewport keeps its box alive")
        void aShadowReachingIn() {
            try (var tree = RenderTree.create()) {
                var raised = Box.of()
                        .direction(FlexDirection.COLUMN)
                        .size(Length.points(100), Length.points(100))
                        .overflow(Overflow.HIDDEN)
                        .children(row(0x00000000, 20)
                                .decoration(new Decoration(
                                        Corners.SQUARE, 0, 0, 0, 0, 0, new Shadow(0, 16, 32, 0, 0xFF000000)))
                                .transform(upBy(-24)));

                tree.update(target.frame(), raised);
                tree.paint(target.frame());

                assertEquals(0, tree.boxesCulled(), "the shadow reaches 32px below a box 4px above the edge");
                assertTrue(target.alphaAt(50, 2) > 0, "and it is drawn");
            }
        }
    }

    @Nested
    @DisplayName("and it keeps up")
    class Reconciled {

        /// The cache in `RenderObject.settle` reuses last frame's ink whenever
        /// nothing in a subtree changed and its rectangle held. This is the case
        /// that found the hole in the first version: a parent that starts
        /// wrapping moves every child without changing one field of any child's
        /// box ([ADR-0313]).
        @Test
        @DisplayName("a row that starts wrapping re-measures the children it moved")
        void wrappingInvalidatesTheCache() {
            try (var tree = RenderTree.create()) {
                // Forty tall, so the two lines a wrap makes are twenty each and
                // the second child lands exactly where its own height puts it --
                // a taller row would have Yoga distributing the slack, which is
                // a different thing to be asserting.
                var row = Box.of()
                        .direction(FlexDirection.ROW)
                        .size(Length.points(100), Length.points(40))
                        .overflow(Overflow.HIDDEN)
                        .children(
                                Box.filled(GREEN).size(Length.points(60), Length.points(20)),
                                Box.filled(RED).size(Length.points(60), Length.points(20)));
                // These two *do* shrink: a row that squeezes them onto one line
                // and a row that wraps them onto two is the difference being
                // asserted, and `shrink(0)` would take the first case away.

                tree.update(target.frame(), row.wrap(io.github.digitalsmile.goldberry.layout.Wrap.NO_WRAP));
                tree.paint(target.frame());
                assertEquals(GREEN, target.pixel(10, 10), "squeezed onto one line");

                tree.update(target.frame(), row.wrap(io.github.digitalsmile.goldberry.layout.Wrap.WRAP));
                tree.paint(target.frame());

                assertEquals(RED, target.pixel(10, 30), "the second child is on a second line, and was drawn there");
            }
        }

        @Test
        @DisplayName("a viewport that grows draws the rows it just made room for")
        void growingRevealsMore() {
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), viewport(40, 50));
                tree.paint(target.frame());
                assertEquals(2 + 1, tree.boxesPainted());

                tree.update(target.frame(), viewport(100, 50));
                tree.paint(target.frame());

                assertEquals(5 + 1, tree.boxesPainted(), "three more rows fit, and three more are drawn");
                assertEquals(GREEN, target.pixel(50, 90));
            }
        }
    }
}
