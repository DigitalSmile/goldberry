package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A pointer routed through a transform — the correctness trap ADR-0067 named
/// and ADR-0068 closed.
///
/// Every assertion here is one that a transform applied by the painter and
/// ignored by hit testing would fail. That failure has no error and no wrong
/// pixel: the control is drawn exactly where the stylesheet asked, and simply
/// does not respond where it looks like it should. It is the reason `transform`
/// waited for its own change rather than arriving inside ADR-0067's.
class TransformedHitTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static List<HitTest.Region> capture(Box root) {
        var target = TestFrames.of(200, 200, 1.0f);
        try {
            return HitTest.capture(target.frame(), root);
        } finally {
            target.end();
        }
    }

    /// A 40×40 box at the top-left of a 200×200 frame, with `transform` on it.
    private static Box moved(Transform transform) {
        return Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.filled(0xFF00FF00)
                        .size(StyleLength.points(40), StyleLength.points(40))
                        .transform(transform)
                        .owner("target"));
    }

    @Test
    @DisplayName("a translated box is hit where it was drawn, not where it was laid out")
    void translated() {
        // Yoga puts it at (0,0)-(40,40); the transform draws it at (100,100).
        var regions = capture(moved(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(100), Transform.Length.px(100)))));

        assertEquals("target", HitTest.at(regions, 120, 120).orElseThrow(),
                "the pointer is over the ink");
        assertFalse(HitTest.at(regions, 20, 20).filter("target"::equals).isPresent(),
                "and not over the rectangle Yoga produced, which nothing is drawn in");
    }

    @Test
    @DisplayName("a scaled box grows its hit area from its own middle")
    void scaled() {
        // 40x40 at the origin scaled by two about `50% 50%` covers (-20,-20) to
        // (60,60). The centre is unmoved and the far corner is now inside.
        var regions = capture(moved(Transform.of(new Transform.Function.Scale(2, 2))));

        assertEquals("target", HitTest.at(regions, 20, 20).orElseThrow(), "the centre");
        assertEquals("target", HitTest.at(regions, 55, 55).orElseThrow(), "newly covered");
        assertFalse(HitTest.at(regions, 65, 65).filter("target"::equals).isPresent(),
                "and it stops where the ink stops");
    }

    @Test
    @DisplayName("a rotated box is not its bounding box")
    void rotated() {
        // 40x40 rotated 45 degrees about its middle is a diamond. Its corners
        // reach further than the square did, and its own corners have swung
        // inside. Testing the bounding box would get both of these wrong, which
        // is what mapping the pointer through the inverse avoids.
        var regions = capture(moved(
                Transform.of(new Transform.Function.Rotate(Math.toRadians(45)))));

        assertEquals("target", HitTest.at(regions, 20, -6).orElseThrow(),
                "the top of the diamond, outside the original square");
        assertFalse(HitTest.at(regions, 2, 2).filter("target"::equals).isPresent(),
                "the square's own top-left corner is now outside the shape");
    }

    @Test
    @DisplayName("a child inherits its parent's transform")
    void nested() {
        // The move is on the parent; the assertion is about the child, which
        // never mentions a transform. A painter that accumulates and a hit test
        // that does not would put the child's ink at 110 and its hit area at 10.
        var child = Box.filled(0xFF0000FF)
                .size(StyleLength.points(20), StyleLength.points(20))
                .owner("child");
        var parent = Box.filled(0xFF00FF00)
                .size(StyleLength.points(40), StyleLength.points(40))
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.px(100), Transform.Length.ZERO)))
                .children(child);
        var regions = capture(Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(parent));

        assertEquals("child", HitTest.at(regions, 110, 10).orElseThrow());
    }

    @Test
    @DisplayName("nested transforms compose rather than replace")
    void nestedCompose() {
        // Parent moves right 100, child moves down 50. The child must end up at
        // both, which is the assertion that fails if the walk overwrites the
        // accumulated matrix instead of composing under it.
        var child = Box.filled(0xFF0000FF)
                .size(StyleLength.points(20), StyleLength.points(20))
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.ZERO, Transform.Length.px(50))))
                .owner("child");
        var parent = Box.filled(0xFF00FF00)
                .size(StyleLength.points(40), StyleLength.points(40))
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.px(100), Transform.Length.ZERO)))
                .children(child);
        var regions = capture(Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(parent));

        assertEquals("child", HitTest.at(regions, 110, 60).orElseThrow());
    }

    @Test
    @DisplayName("a box scaled to nothing is not hit anywhere")
    void collapsed() {
        // `scale(0)` has no inverse: every point on screen maps into the box, so
        // a naive implementation would route every click in the window to it.
        // The region is dropped instead.
        var regions = capture(moved(Transform.of(new Transform.Function.Scale(0, 0))));

        assertTrue(regions.stream().noneMatch(r -> "target".equals(r.owner())),
                "a collapsed box has no rectangle to be inside of");
        assertFalse(HitTest.at(regions, 20, 20).filter("target"::equals).isPresent());
    }

    @Test
    @DisplayName("an untransformed box carries no inverse and pays for nothing")
    void plainBoxesAreUnchanged() {
        // The hot path: a window of forty controls, none of them transformed,
        // must cost the four comparisons it always did rather than a matrix
        // multiply each.
        var regions = capture(Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.filled(0xFF00FF00)
                        .size(StyleLength.points(40), StyleLength.points(40))
                        .owner("target")));

        assertTrue(regions.stream().allMatch(r -> r.inverse() == null));
        assertEquals("target", HitTest.at(regions, 20, 20).orElseThrow());
    }

    @Test
    @DisplayName("the cursor follows the transform too")
    void cursor() {
        // `cursor` inherits through the stack of painted rectangles rather than
        // the element tree (ADR-0057), so it is answered by the same `contains`
        // and would go wrong in exactly the same way.
        var regions = capture(Box.filled(0xFF000000)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.filled(0xFF00FF00)
                        .size(StyleLength.points(40), StyleLength.points(40))
                        .cursor(io.github.digitalsmile.goldberry.backend.Cursor.POINTER)
                        .transform(Transform.of(new Transform.Function.Translate(
                                Transform.Length.px(100), Transform.Length.px(100))))));

        assertEquals(io.github.digitalsmile.goldberry.backend.Cursor.POINTER,
                HitTest.cursorAt(regions, 120, 120));
        assertEquals(io.github.digitalsmile.goldberry.backend.Cursor.DEFAULT,
                HitTest.cursorAt(regions, 20, 20));
    }
}
