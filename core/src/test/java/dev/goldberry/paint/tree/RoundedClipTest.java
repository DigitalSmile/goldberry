package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Decoration;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Overflow;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;

/// `overflow: hidden` with a `border-radius` clips the children to the curve —
/// and with square corners it costs exactly what it always did.
///
/// Counts and pixels both, for [CullingTest]'s reason: a pixel says the corner
/// was cut, and only a count says a box with square corners took no layer and a
/// still rounded one was blitted rather than drawn again.
@DisplayName("clipping children to a border-radius")
class RoundedClipTest {

    private static final int BLUE = 0xFF0000FF;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(100, 100, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// A blue page holding one 40×40 box of radius `radius` that clips, filled
    /// edge to edge by a red child.
    private static Box scene(double radius) {
        var face = Box.of()
                .size(Length.points(40), Length.points(40))
                .shrink(0)
                .overflow(Overflow.HIDDEN)
                .decoration(Decoration.NONE.radius(radius))
                .children(Box.filled(RED)
                        .size(Length.points(40), Length.points(40))
                        .shrink(0));
        return Box.filled(BLUE).size(Length.points(100), Length.points(100)).children(face);
    }

    @Test
    @DisplayName("a child that fills a round box is cut to the circle, and the page shows in the corners")
    void theCornerIsCut() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), scene(20));
            tree.paint(target.frame());

            assertEquals(BLUE, target.pixel(1, 1), "the child was drawn in the corner the curve cuts off");
            assertEquals(BLUE, target.pixel(38, 38), "and in the opposite one");
            assertEquals(RED, target.pixel(20, 20), "the middle of the child is drawn");
            assertEquals(RED, target.pixel(20, 1), "and the top of the circle");
        }
    }

    @Test
    @DisplayName("square corners clip to the rectangle, with no layer, as they always did")
    void squareCornersTakeNoLayer() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), scene(0));
            tree.paint(target.frame());

            assertEquals(RED, target.pixel(1, 1));
            assertEquals(BLUE, target.pixel(41, 41), "nothing is drawn outside the rectangle");
            assertEquals(0, tree.layersComposited(), "a square clip went through a layer");
        }
    }

    @Test
    @DisplayName("padding wider than the radius keeps the children out of the corners, and takes no layer")
    void paddingClearOfTheCorners() {
        try (var tree = RenderTree.create()) {
            var field = Box.of()
                    .size(Length.points(60), Length.points(30))
                    .shrink(0)
                    .padding(Length.points(8))
                    .overflow(Overflow.HIDDEN)
                    .decoration(Decoration.NONE.radius(6))
                    .children(Box.filled(RED)
                            .size(Length.points(80), Length.points(40))
                            .shrink(0));
            tree.update(
                    target.frame(),
                    Box.filled(BLUE)
                            .size(Length.points(100), Length.points(100))
                            .children(field));
            tree.paint(target.frame());

            assertEquals(0, tree.layersComposited(), "a clip the curve cannot reach went through a layer");
            assertEquals(RED, target.pixel(9, 9), "the child is drawn inside the padding");
            assertEquals(BLUE, target.pixel(4, 4), "and nothing in the padding");
        }
    }

    @Test
    @DisplayName("a still round box is blitted again, not drawn again")
    void aStillBoxIsABlit() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), scene(20));
            tree.paint(target.frame());
            assertEquals(1, tree.layersRepainted());

            tree.update(target.frame(), scene(20));
            tree.paint(target.frame());

            assertEquals(1, tree.layersComposited());
            assertEquals(0, tree.layersRepainted(), "nothing under the box changed and its children were drawn again");
            assertEquals(BLUE, target.pixel(1, 1));
            assertEquals(RED, target.pixel(20, 20));
        }
    }

    @Test
    @DisplayName("a child that changes is drawn again, and cut again")
    void aChangedChildIsDrawnAgain() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), scene(20));
            tree.paint(target.frame());

            var face = Box.of()
                    .size(Length.points(40), Length.points(40))
                    .shrink(0)
                    .overflow(Overflow.HIDDEN)
                    .decoration(Decoration.NONE.radius(20))
                    .children(Box.filled(GREEN)
                            .size(Length.points(40), Length.points(40))
                            .shrink(0));
            tree.update(
                    target.frame(),
                    Box.filled(BLUE)
                            .size(Length.points(100), Length.points(100))
                            .children(face));
            tree.paint(target.frame());

            assertEquals(1, tree.layersRepainted());
            assertEquals(GREEN, target.pixel(20, 20));
            assertEquals(BLUE, target.pixel(1, 1));
        }
    }

    @Test
    @DisplayName("the curve is inside the border: a ringed box cuts its child at the inner radius")
    void insideTheBorder() {
        try (var tree = RenderTree.create()) {
            var face = Box.of()
                    .size(Length.points(40), Length.points(40))
                    .shrink(0)
                    .overflow(Overflow.HIDDEN)
                    .decoration(Decoration.NONE.radius(20).border(4, GREEN))
                    .children(Box.filled(RED)
                            .size(Length.points(40), Length.points(40))
                            .shrink(0));
            tree.update(
                    target.frame(),
                    Box.filled(BLUE)
                            .size(Length.points(100), Length.points(100))
                            .children(face));
            tree.paint(target.frame());

            // On the diagonal, just inside where the border's inner edge curves:
            // a child cut at the outer radius would show here, one cut at the
            // inner radius does not.
            var corner = target.pixel(7, 7);
            assertTrue(
                    corner != RED,
                    () -> "the child shows between the border's curve and the box's corner: "
                            + Integer.toHexString(corner));
        }
    }

    @Test
    @DisplayName("two rounded clips nest: the inner box is cut by both curves")
    void nested() {
        try (var tree = RenderTree.create()) {
            var inner = Box.of()
                    .size(Length.points(60), Length.points(60))
                    .shrink(0)
                    .overflow(Overflow.HIDDEN)
                    .decoration(Decoration.NONE.radius(10))
                    .children(Box.filled(RED)
                            .size(Length.points(60), Length.points(60))
                            .shrink(0));
            var outer = Box.of()
                    .size(Length.points(40), Length.points(40))
                    .shrink(0)
                    .overflow(Overflow.HIDDEN)
                    .decoration(Decoration.NONE.radius(20))
                    .children(inner);
            tree.update(
                    target.frame(),
                    Box.filled(BLUE)
                            .size(Length.points(100), Length.points(100))
                            .children(outer));
            tree.paint(target.frame());

            assertEquals(BLUE, target.pixel(1, 1), "the outer curve did not cut the inner box");
            assertEquals(RED, target.pixel(20, 20));
            assertEquals(BLUE, target.pixel(38, 38), "the outer box's far corner");
            assertEquals(2, tree.layersComposited(), "one layer per rounded clip");
        }
    }
}
