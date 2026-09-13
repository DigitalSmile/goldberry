package io.github.digitalsmile.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// Where a box with a margin lands — ADR-0311.
///
/// Against the compiled Yoga rather than against the arithmetic, for
/// `AbsolutePlacementTest`'s reason: the toolkit's claim is not "the right number
/// was written onto the node", it is "the box ends up here". The two are the same
/// claim only while nothing else in the pipeline is wrong.
class MarginTest {

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

    /// A 200×100 row with no padding, holding `kids`.
    private static Box row(Box... kids) {
        return Box.filled(PARENT)
                .size(px(200), px(100))
                .direction(FlexDirection.ROW)
                .children(kids);
    }

    private static Box child() {
        return Box.filled(CHILD).size(px(40), px(20));
    }

    private List<LogicalRect> laidOut(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            var out = new ArrayList<LogicalRect>();
            tree.forEachPlacedBox(placed -> out.add(placed.layout()));
            return out;
        }
    }

    @Nested
    @DisplayName("room outside the box")
    class Outside {

        @Test
        @DisplayName("a margin moves the box and does not resize it")
        void movesRatherThanGrows() {
            // The difference from `padding` in one assertion. A widget that
            // wanted space around itself and reached for padding got a box 24px
            // wider with its content still in the corner.
            var placed = laidOut(row(child().margin(px(12)))).get(1);

            assertEquals(LogicalRect.of(12f, 12f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("padding on the same child does not move it")
        void paddingIsTheOtherThing() {
            // The same declaration, the other side of the box: the child stays in
            // the corner and the 5px goes inside it, where a fixed size absorbs
            // it because a size in CSS is a border-box size. Push the padding
            // past that size and the box grows rather than moving -- `padding:
            // 12px` on a 20px-tall child comes out 24 tall, which is Yoga
            // enforcing that a box is at least its own padding.
            assertEquals(
                    LogicalRect.of(0f, 0f, 40f, 20f),
                    laidOut(row(child().padding(px(5)))).get(1));
            assertEquals(24f, laidOut(row(child().padding(px(12)))).get(1).height());
        }

        @Test
        @DisplayName("the four edges are CSS's, clockwise from the top")
        void edgesAreNotSwapped() {
            // The transcription bug `Insets` exists to prevent, asserted where it
            // would actually show: a top of 3 and a left of 9 must not arrive as
            // (3, 9).
            var placed = laidOut(row(child().margin(new Insets(px(3), px(5), px(7), px(9)))))
                    .get(1);

            assertEquals(LogicalRect.of(9f, 3f, 40f, 20f), placed);
        }

        @Test
        @DisplayName("two siblings' margins add rather than collapsing")
        void noCollapsing() {
            // CSS collapses adjacent vertical margins in **block** layout and
            // never in flex, and this is a flex engine: 10 and 6 between two
            // boxes is 16, not 10. Asserted because it is the first thing an
            // author who learnt CSS on documents expects to be wrong.
            var placed = laidOut(row(
                    child().margin(new Insets(px(0), px(10), px(0), px(0))),
                    child().margin(new Insets(px(0), px(0), px(0), px(6)))));

            assertEquals(0f, placed.get(1).left());
            assertEquals(56f, placed.get(2).left(), "40 wide, then 10 + 6 of margin");
        }

        @Test
        @DisplayName("a negative margin pulls a box over its neighbour")
        void negative() {
            // Deliberately not clamped: a row of overlapping avatars is written
            // this way, and so is a control escaping its container's padding on
            // one edge.
            var placed = laidOut(row(child(), child().margin(new Insets(px(0), px(0), px(0), px(-15)))));

            assertEquals(25f, placed.get(2).left(), "40 wide, less 15 pulled back");
        }
    }

    @Nested
    @DisplayName("`auto`, which is the reason the property was wanted")
    class Auto {

        @Test
        @DisplayName("`margin: 0 auto` centres a box on the main axis")
        void centres() {
            // The thing nothing in the subset could do. `align-self: center`
            // centres on the **cross** axis; `justify-content` is the container's
            // decision about all its children at once. A box centring itself in a
            // row it does not control had no spelling at all.
            var placed = laidOut(row(child().margin(new Insets(px(0), Length.AUTO, px(0), Length.AUTO))))
                    .get(1);

            assertEquals(80f, placed.left(), "200 less 40, halved");
            assertEquals(40f, placed.width(), "and it is not stretched to fill");
        }

        @Test
        @DisplayName("`margin-left: auto` pushes one box to the end of a row")
        void pushesToTheEnd() {
            // The other half of the same call, and the one a toolbar wants: two
            // buttons on the left, one on the right, with no spacer box between
            // them.
            var placed = laidOut(row(child(), child().margin(new Insets(px(0), px(0), px(0), Length.AUTO))));

            assertEquals(0f, placed.get(1).left());
            assertEquals(160f, placed.get(2).left(), "pushed against the far edge");
        }

        @Test
        @DisplayName("an auto margin absorbs space that `justify-content` would otherwise spread")
        void beatsJustifyContent() {
            var placed = laidOut(row(child(), child().margin(new Insets(px(0), px(0), px(0), Length.AUTO))));

            assertTrue(placed.get(2).left() > placed.get(1).left() + 40, "the free space went to the margin");
        }
    }

    @Nested
    @DisplayName("what it costs")
    class Cost {

        @Test
        @DisplayName("a box with no margin lays out exactly where it did before the property existed")
        void zeroChangesNothing() {
            // The regression this guards: `Insets.ZERO` is the default on every
            // box in the toolkit, and applying it must be indistinguishable from
            // not applying it. `RenderObject` skips the four calls entirely on a
            // first apply for exactly this reason.
            assertEquals(
                    laidOut(row(child(), child())),
                    laidOut(row(child().margin(Insets.ZERO), child().margin(Insets.ZERO))));
        }

        @Test
        @DisplayName("a margin that changes between frames moves the box")
        void reapplied() {
            // `RenderObject.apply` guards each property against the previous
            // frame's, and a guard that compared the wrong thing would leave the
            // box where it was. Two updates on one tree, which is the shape a
            // window actually runs in.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), row(child()));
                tree.update(target.frame(), row(child().margin(px(20))));

                var out = new ArrayList<LogicalRect>();
                tree.forEachPlacedBox(placed -> out.add(placed.layout()));
                assertEquals(LogicalRect.of(20f, 20f, 40f, 20f), out.get(1));
            }
        }

        @Test
        @DisplayName("an absolutely positioned box takes its margin too")
        void onAnAbsoluteChild() {
            // Yoga applies margin to an out-of-flow node as an offset from where
            // the inset put it, which is CSS's rule. Asserted because the inset
            // path is the one the toolkit already had to correct (ADR-0272), and
            // a second offset stacked on a corrected one is worth pinning.
            var placed = laidOut(row(child().position(Position.ABSOLUTE)
                            .inset(new Insets(px(0), Length.UNDEFINED, Length.UNDEFINED, px(0)))
                            .margin(px(7))))
                    .get(1);

            assertEquals(LogicalRect.of(7f, 7f, 40f, 20f), placed);
        }
    }
}
