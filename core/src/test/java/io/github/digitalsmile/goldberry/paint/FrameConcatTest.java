package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;

/// A transform that composes with the one the frame is already under
/// (ADR-0390, `docs/gaps.md` G46).
///
/// Pixels rather than matrices, for [TransformPaintTest]'s reason: the matrix
/// crosses into C as a `void*`, and a composition done in the wrong order is a
/// perfectly valid frame with the ink in the wrong place.
class FrameConcatTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static final int GREEN = 0xFF00FF00;

    /// A 200x200 black frame, with `painting` run on it.
    private static TestFrames.Target paint(float scale, Consumer<Frame> painting) {
        var target = TestFrames.of((int) (200 * scale), (int) (200 * scale), scale);
        try {
            target.frame().fill(0xFF000000);
            painting.accept(target.frame());
        } finally {
            target.end();
        }
        return target;
    }

    private static void assertGreen(TestFrames.Target target, int x, int y) {
        assertEquals(GREEN, target.pixel(x, y), () -> "expected the box at (" + x + ", " + y + ")");
    }

    private static void assertNotGreen(TestFrames.Target target, int x, int y) {
        assertTrue(target.pixel(x, y) != GREEN, () -> "expected nothing at (" + x + ", " + y + ")");
    }

    @Test
    @DisplayName("a concat composes with the transform already in force")
    void composes() {
        var target = paint(1.0f, frame -> {
            frame.transform(1, 0, 0, 1, 100, 0);
            frame.concat(1, 0, 0, 1, 0, 50);
            frame.fillRect(0, 0, 20, 20, GREEN);
        });

        assertGreen(target, 105, 55);
        assertNotGreen(target, 105, 5);
        assertNotGreen(target, 5, 55);
    }

    @Test
    @DisplayName("a transform replaces what a concat would have composed with")
    void replaces() {
        // The two calls side by side, because the difference between them is the
        // whole of G46: a canvas painter that sets a matrix throws away the one
        // that put the canvas on screen.
        var target = paint(1.0f, frame -> {
            frame.transform(1, 0, 0, 1, 100, 0);
            frame.transform(1, 0, 0, 1, 0, 50);
            frame.fillRect(0, 0, 20, 20, GREEN);
        });

        assertGreen(target, 5, 55);
        assertNotGreen(target, 105, 55);
    }

    @Test
    @DisplayName("the caller's matrix is applied first, in the coordinates it draws in")
    void appliesTheCallersMatrixFirst() {
        // Scaled by two, then offset by ten: the offset is the painter's own, so
        // it is scaled with everything else and lands at twenty. Multiplying the
        // other way round would put it at ten and nothing else would look wrong.
        var target = paint(1.0f, frame -> {
            frame.transform(2, 0, 0, 2, 0, 0);
            frame.concat(1, 0, 0, 1, 10, 0);
            frame.fillRect(0, 0, 10, 10, GREEN);
        });

        assertGreen(target, 25, 5);
        assertNotGreen(target, 15, 5);
        assertNotGreen(target, 45, 5);
    }

    @Test
    @DisplayName("a concat moves by logical pixels at any display scale")
    void logicalAtAnyScale() {
        // The display scale is on the context underneath, and a composition that
        // folded it in again would move by fifteen device pixels per ten logical
        // ones asked for.
        var target = paint(1.5f, frame -> {
            frame.transform(1, 0, 0, 1, 100, 0);
            frame.concat(1, 0, 0, 1, 10, 0);
            frame.fillRect(0, 0, 20, 20, GREEN);
        });

        // 110 logical is 165 physical, and 20 logical wide is 30.
        assertGreen(target, 170, 10);
        assertGreen(target, 190, 10);
        assertNotGreen(target, 160, 10);
        assertNotGreen(target, 200, 10);
    }

    @Test
    @DisplayName("a restore puts back the transform a concat changed")
    void restored() {
        // What a canvas painter is handed the pair for: compose, draw, and leave
        // the frame as it was found.
        var target = paint(1.0f, frame -> {
            frame.transform(1, 0, 0, 1, 100, 0);
            frame.save();
            frame.concat(1, 0, 0, 1, 0, 50);
            frame.restore();
            frame.fillRect(0, 0, 20, 20, GREEN);
        });

        assertGreen(target, 105, 5);
        assertNotGreen(target, 105, 55);
    }

    @Test
    @DisplayName("a reset transform is what the next concat composes with")
    void resetIsTheIdentity() {
        var target = paint(1.0f, frame -> {
            frame.transform(1, 0, 0, 1, 100, 0);
            frame.resetTransform();
            frame.concat(1, 0, 0, 1, 0, 50);
            frame.fillRect(0, 0, 20, 20, GREEN);
        });

        assertGreen(target, 5, 55);
        assertNotGreen(target, 105, 55);
    }
}
