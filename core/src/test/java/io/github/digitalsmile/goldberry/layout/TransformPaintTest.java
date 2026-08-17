package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A transform reaching Blend2D — the other half of what
/// [io.github.digitalsmile.goldberry.input.TransformedHitTest] asserts.
///
/// These read pixels back rather than inspecting matrices, because the matrix
/// crosses into C as a `void*` and the only thing that proves the six doubles
/// landed in the fields Blend2D reads is where the ink ended up. A transposed
/// matrix, a row/column mix-up, or a scale applied twice all produce a valid
/// frame and `BL_SUCCESS`.
class TransformPaintTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static final int GREEN = 0xFF00FF00;

    /// A 200×200 black frame with a 40×40 green box at the top-left, transformed.
    private static TestFrames.Target paint(Transform transform, float scale) {
        var target = TestFrames.of((int) (200 * scale), (int) (200 * scale), scale);
        try {
            BoxPainter.paint(target.frame(), Box.filled(0xFF000000)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .children(Box.filled(GREEN)
                            .size(StyleLength.points(40), StyleLength.points(40))
                            .transform(transform)));
        } finally {
            target.end();
        }
        return target;
    }

    private static void assertGreen(TestFrames.Target target, int x, int y) {
        assertEquals(GREEN, target.pixel(x, y),
                () -> "expected the box at (" + x + ", " + y + ")");
    }

    private static void assertNotGreen(TestFrames.Target target, int x, int y) {
        assertTrue(target.pixel(x, y) != GREEN,
                () -> "expected nothing at (" + x + ", " + y + ")");
    }

    @Test
    @DisplayName("a translate moves the ink, and by the amount asked for")
    void translate() {
        var target = paint(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(100), Transform.Length.px(60))), 1.0f);

        assertGreen(target, 120, 80);
        assertNotGreen(target, 20, 20);
        // The far edge, so a transform that moved by the right amount in the
        // wrong direction on one axis is caught: (139, 99) is inside and
        // (141, 101) is not.
        assertGreen(target, 139, 99);
        assertNotGreen(target, 141, 101);
    }

    @Test
    @DisplayName("a scale grows the ink about the box's middle")
    void scale() {
        // 40x40 at the origin, scaled by two about `50% 50%`: (-20,-20)-(60,60).
        var target = paint(Transform.of(new Transform.Function.Scale(2, 2)), 1.0f);

        assertGreen(target, 55, 55);
        assertNotGreen(target, 65, 65);
        assertGreen(target, 1, 1);
    }

    @Test
    @DisplayName("a rotation puts ink outside the rectangle and clears the corners")
    void rotate() {
        // A 40x40 square rotated 45 degrees about its middle: the corner at
        // (20, -6) is covered and the original (2, 2) corner is not. Exactly the
        // two points TransformedHitTest asserts about the pointer, which is the
        // point -- the ink and the hit area are the same shape.
        var target = paint(Transform.of(
                new Transform.Function.Rotate(Math.toRadians(45))), 1.0f);

        assertGreen(target, 20, 4);
        assertNotGreen(target, 2, 2);
        assertNotGreen(target, 38, 2);
    }

    @Test
    @DisplayName("the display scale is applied once, not twice and not at all")
    void doesNotDoubleTheDisplayScale() {
        // The bug this exists for: the context is already scaled when the frame
        // begins, and BL_TRANSFORM_OP_ASSIGN *replaces* rather than composes. A
        // transform that forgot to fold the scale back in would draw at 100%
        // inside a 150% window; one that applied it twice would draw at 225%.
        // Either is a plausible frame that no assertion on a matrix would catch.
        var target = paint(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(100), Transform.Length.ZERO)), 1.5f);

        // 100 logical is 150 physical, and the box is 40 logical = 60 physical.
        assertGreen(target, 155, 5);
        assertGreen(target, 205, 5);
        assertNotGreen(target, 215, 5);
        assertNotGreen(target, 145, 5);
    }

    @Test
    @DisplayName("a transform ends with the subtree that declared it")
    void doesNotLeak() {
        // A sibling painted after a transformed box must be where Yoga put it.
        // Without an explicit reset the context keeps the last matrix, and the
        // bug shows up on whatever draws next -- which may be an application's
        // own `onPaint` code, a long way from the stylesheet that caused it.
        var target = TestFrames.of(200, 200, 1.0f);
        try {
            BoxPainter.paint(target.frame(), Box.filled(0xFF000000)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.COLUMN)
                    .children(
                            Box.filled(0xFFFF0000)
                                    .size(StyleLength.points(40), StyleLength.points(40))
                                    .transform(Transform.of(new Transform.Function.Translate(
                                            Transform.Length.px(100), Transform.Length.ZERO))),
                            Box.filled(GREEN)
                                    .size(StyleLength.points(40), StyleLength.points(40))));
        } finally {
            target.end();
        }

        // The second box declared no transform, so it is at (0, 40).
        assertGreen(target, 20, 60);
        assertNotGreen(target, 120, 60);
    }

    @Test
    @DisplayName("a child is moved by its parent's transform")
    void inherits() {
        var target = TestFrames.of(200, 200, 1.0f);
        try {
            BoxPainter.paint(target.frame(), Box.filled(0xFF000000)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .children(Box.filled(0xFFFF0000)
                            .size(StyleLength.points(40), StyleLength.points(40))
                            .transform(Transform.of(new Transform.Function.Translate(
                                    Transform.Length.px(100), Transform.Length.ZERO)))
                            .children(Box.filled(GREEN)
                                    .size(StyleLength.points(20), StyleLength.points(20)))));
        } finally {
            target.end();
        }

        assertGreen(target, 110, 10);
        assertNotGreen(target, 10, 10);
    }
}
