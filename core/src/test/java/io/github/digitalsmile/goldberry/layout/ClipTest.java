package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Affine;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.Overflow;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `overflow` and the clip it puts on the painter — ADR-0114.
///
/// The interesting failures here are not "the clip is off by a pixel". They are
/// the two that a rectangle-per-box painter invites: a clip that does not come
/// **off** when its subtree ends, because Blend2D's `resetClip` goes to the whole
/// surface rather than to the previous one; and a clip that reaches paint but not
/// hit testing, which leaves a row invisible and still clickable.
class ClipTest {

    private TestFrames.Target target;

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

    /// A coloured rectangle that **refuses to shrink**.
    ///
    /// `shrink(0)` and not the default, and it is the whole reason a viewport has
    /// anything to clip: Yoga's default `flex-shrink: 1` squashes a 200-tall
    /// child into a 50-tall parent, so a scroll view built without it lays out
    /// perfectly, clips nothing, and has no content to scroll. The `scroll`
    /// widget sets it on its content for the same reason (ADR-0114, ADR-0076).
    private static Box filled(int argb, double width, double height) {
        return Box.of()
                .background(argb)
                .shrink(0)
                .size(StyleLength.points((float) width), StyleLength.points((float) height));
    }

    /// A 50x50 viewport at the origin holding a 50x200 column — the shape every
    /// scroll view has.
    private static Box viewport(Overflow overflow) {
        return Box.of()
                .size(StyleLength.points(50), StyleLength.points(50))
                .overflow(overflow)
                .direction(FlexDirection.COLUMN)
                .children(filled(0xFFFF0000, 50, 200));
    }

    @Nested
    @DisplayName("the rectangle")
    class Rectangle {

        @Test
        @DisplayName("intersecting narrows and never widens")
        void intersectNarrows() {
            var outer = Clip.of(0, 0, 100, 100);
            var inner = Clip.of(50, 50, 100, 100);
            var both = outer.intersect(inner);
            assertEquals(50, both.left());
            assertEquals(50, both.top());
            assertEquals(100, both.right());
            assertEquals(100, both.bottom());
        }

        @Test
        @DisplayName("NONE is the identity, so an unclipped tree costs nothing")
        void noneIsIdentity() {
            var clip = Clip.of(10, 10, 30, 30);
            assertEquals(clip, Clip.NONE.intersect(clip));
            assertEquals(clip, clip.intersect(Clip.NONE));
            assertTrue(Clip.NONE.isNone());
        }

        @Test
        @DisplayName("two clips that miss each other admit nothing")
        void disjointIsEmpty() {
            var left = Clip.of(0, 0, 10, 10);
            var right = Clip.of(20, 0, 10, 10);
            assertTrue(left.intersect(right).isEmpty());
            assertFalse(left.intersect(Clip.of(5, 5, 10, 10)).isEmpty());
        }

        @Test
        @DisplayName("a translation maps exactly")
        void translateIsExact() {
            var mapped = Clip.of(0, 0, 10, 10).map(Affine.translate(5, 7));
            assertEquals(5, mapped.left());
            assertEquals(7, mapped.top());
            assertEquals(15, mapped.right());
            assertEquals(17, mapped.bottom());
        }

        /// The documented approximation: a rotation cannot be a rectangle, so the
        /// bounding box is taken and the clip lets a little through at the
        /// corners. Asserted so the day someone needs it exact, this fails rather
        /// than the picture being subtly wrong.
        @Test
        @DisplayName("a rotation is bounded, not exact")
        void rotationIsBounded() {
            // 90°: (0,0)-(10,10) maps to (0,-10)-(10,0).
            var mapped = Clip.of(0, 0, 10, 10).map(Affine.rotate(Math.toRadians(90)));
            assertEquals(-10, mapped.left(), 1e-9);
            assertEquals(0, mapped.top(), 1e-9);
            assertEquals(0, mapped.right(), 1e-9);
            assertEquals(10, mapped.bottom(), 1e-9);
        }
    }

    @Nested
    @DisplayName("painting")
    class Painting {

        @Test
        @DisplayName("hidden overflow cuts the content off at the viewport")
        void hiddenClips() {
            BoxPainter.paint(target.frame(), viewport(Overflow.HIDDEN));
            target.end();

            assertEquals(0xFFFF0000, target.pixel(25, 25), "inside the viewport");
            assertEquals(0xFFFF0000, target.pixel(25, 49), "the last row inside it");
            assertEquals(0, target.alphaAt(25, 50), "the first row past it is untouched");
            assertEquals(0, target.alphaAt(25, 150), "and so is everything below");
        }

        @Test
        @DisplayName("visible overflow lets it spill, which is CSS's default")
        void visibleSpills() {
            BoxPainter.paint(target.frame(), viewport(Overflow.VISIBLE));
            target.end();

            assertEquals(0xFFFF0000, target.pixel(25, 25), "inside");
            assertEquals(0xFFFF0000, target.pixel(25, 150), "and well past the viewport");
        }

        /// `scroll` sizes exactly as `hidden` does; the two differ only above the
        /// layout engine, where a widget decides whether to offer bars.
        @Test
        @DisplayName("scroll clips exactly as hidden does")
        void scrollClipsToo() {
            BoxPainter.paint(target.frame(), viewport(Overflow.SCROLL));
            target.end();

            assertEquals(0xFFFF0000, target.pixel(25, 25));
            assertEquals(0, target.alphaAt(25, 50));
        }

        /// The failure Blend2D's flat `resetClip` invites: the clip must come off
        /// when the viewport's subtree ends, or the sibling drawn afterwards is
        /// clipped by a box it has nothing to do with.
        @Test
        @DisplayName("a sibling after the viewport is not clipped by it")
        void clipDoesNotLeak() {
            var root = Box.of()
                    .direction(FlexDirection.COLUMN)
                    .children(
                            viewport(Overflow.HIDDEN),
                            filled(0xFF0000FF, 200, 100));
            BoxPainter.paint(target.frame(), root);
            target.end();

            assertEquals(0xFFFF0000, target.pixel(25, 25), "the viewport still clips");
            assertEquals(0xFFFF0000, target.pixel(25, 49), "out to its last row");
            assertEquals(0xFF0000FF, target.pixel(100, 60), "the sibling paints in full");
            assertEquals(0xFF0000FF, target.pixel(190, 140), "out to its own far corner");
        }

        /// Nested viewports: the inner one narrows the outer and cannot widen it.
        /// The case that cannot be expressed with one native clip at a time,
        /// and the whole reason the stack is in Java.
        @Test
        @DisplayName("a viewport inside a viewport intersects, and unwinds one level")
        void nestedClipsIntersect() {
            var inner = Box.of()
                    .size(StyleLength.points(200), StyleLength.points(20))
                    .overflow(Overflow.HIDDEN)
                    .direction(FlexDirection.COLUMN)
                    .children(filled(0xFF00FF00, 200, 200));
            var outer = Box.of()
                    .size(StyleLength.points(50), StyleLength.points(200))
                    .overflow(Overflow.HIDDEN)
                    .direction(FlexDirection.COLUMN)
                    .children(inner, filled(0xFF0000FF, 200, 100));
            BoxPainter.paint(target.frame(), outer);
            target.end();

            assertEquals(0xFF00FF00, target.pixel(25, 10), "inside both");
            assertEquals(0, target.alphaAt(60, 10), "the outer clip still holds at x=60");
            assertEquals(0xFF00FF00, target.pixel(25, 19), "the green runs to the inner edge");
            // The sibling after the inner viewport: back to the outer clip, not
            // to no clip at all.
            assertEquals(0xFF0000FF, target.pixel(25, 30), "the sibling paints inside the outer");
            assertEquals(0, target.alphaAt(60, 30), "and is still cut off at the outer edge");
        }

        /// CSS clips a box's *content*, not the box. A viewport keeps its own
        /// background and border.
        @Test
        @DisplayName("the clip is the padding box, so content scrolls under the border")
        void clipsToPaddingBox() {
            var root = Box.of()
                    .background(0xFF0000FF)
                    .size(StyleLength.points(50), StyleLength.points(50))
                    .padding(Insets.all(StyleLength.points(10)))
                    .overflow(Overflow.HIDDEN)
                    .direction(FlexDirection.COLUMN)
                    .children(filled(0xFFFF0000, 50, 200));
            BoxPainter.paint(target.frame(), root);
            target.end();

            assertEquals(0xFF0000FF, target.pixel(5, 5), "the viewport's own background");
            assertEquals(0xFFFF0000, target.pixel(25, 25), "the content inside the padding");
            assertEquals(0xFF0000FF, target.pixel(25, 45), "padding, not content, at the bottom");
        }

        @Test
        @DisplayName("a translated viewport clips where it is drawn, not where it was laid out")
        void clipFollowsTheTransform() {
            var root = Box.of()
                    .direction(FlexDirection.COLUMN)
                    .children(viewport(Overflow.HIDDEN)
                            .transform(Transform.of(new Transform.Function.Translate(
                                    Transform.Length.px(100), Transform.Length.ZERO))));
            BoxPainter.paint(target.frame(), root);
            target.end();

            assertEquals(0, target.alphaAt(25, 25), "nothing where it was laid out");
            assertEquals(0xFFFF0000, target.pixel(125, 25), "drawn where the transform put it");
            assertEquals(0, target.alphaAt(125, 60), "and clipped there too");
        }
    }

    @Nested
    @DisplayName("hit testing")
    class Hits {

        /// ARCHITECTURE §11 has promised since it was written that hit testing
        /// "respects clips and transforms". The transform half was true; this half
        /// had nothing to be true about until now.
        @Test
        @DisplayName("a box clipped away is not clickable")
        void clippedAwayIsNotHit() {
            var owner = new Object();
            var content = filled(0xFFFF0000, 50, 200).owner(owner);
            var root = Box.of()
                    .size(StyleLength.points(50), StyleLength.points(50))
                    .overflow(Overflow.HIDDEN)
                    .direction(FlexDirection.COLUMN)
                    .children(content);

            var regions = HitTest.capture(target.frame(), root);
            assertEquals(owner, HitTest.at(regions, 25, 25).orElse(null),
                    "inside the viewport, the content takes the pointer");
            assertTrue(HitTest.at(regions, 25, 120).isEmpty(),
                    "past the viewport it is invisible, so it is not clickable either");
        }

        @Test
        @DisplayName("visible overflow stays clickable where it spills")
        void visibleStaysClickable() {
            var owner = new Object();
            var content = filled(0xFFFF0000, 50, 200).owner(owner);
            var root = Box.of()
                    .size(StyleLength.points(50), StyleLength.points(50))
                    .overflow(Overflow.VISIBLE)
                    .direction(FlexDirection.COLUMN)
                    .children(content);

            var regions = HitTest.capture(target.frame(), root);
            assertEquals(owner, HitTest.at(regions, 25, 120).orElse(null),
                    "it spills, so it is hit where it spills");
        }
    }
}
