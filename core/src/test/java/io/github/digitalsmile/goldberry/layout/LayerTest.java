package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Layer promotion — ADR-0071.
///
/// The reason this is worth its own file rather than a golden: a golden says two
/// images differ, and the whole question here is *how*. CSS group opacity and a
/// per-box alpha multiply produce images that look nearly alike and differ in
/// one specific place, so the assertions are on that place.
class LayerTest {

    private static final int RED = 0xFFBF616A;
    private static final int GREEN = 0xFFA3BE8C;
    private static final int BACKDROP = 0xFF2E3440;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // Synchronous, so the pixels are final when the frame ends rather than
        // queued behind Blend2D's workers.
        target = TestFrames.of(200, 200, 1.0f, 0);
    }

    private static Box square(int argb, float size) {
        return Box.filled(argb).size(StyleLength.points(size), StyleLength.points(size));
    }

    /// Two 60x60 squares in a row, the second pulled back 30 so they overlap,
    /// under a parent at `opacity`.
    private static Box overlapping(double opacity) {
        return Box.filled(BACKDROP)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.of()
                        .opacity(opacity)
                        .direction(FlexDirection.ROW)
                        .children(
                                square(RED, 60),
                                square(GREEN, 60).transform(Transform.of(
                                        new Transform.Function.Translate(
                                                Transform.Length.px(-30), Transform.Length.ZERO)))));
    }

    /// The same tree, with the promoted group itself translated.
    private static Box moved(double opacity, float by) {
        return Box.filled(BACKDROP)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.of()
                        .opacity(opacity)
                        .transform(Transform.of(new Transform.Function.Translate(
                                Transform.Length.px(by), Transform.Length.ZERO)))
                        .direction(FlexDirection.ROW)
                        .children(square(RED, 60), square(GREEN, 60)));
    }

    /// A promoted group whose *child* carries the second opacity.
    private static Box childFaded(double groupOpacity, double childOpacity) {
        return Box.filled(BACKDROP)
                .size(StyleLength.points(200), StyleLength.points(200))
                .children(Box.of()
                        .opacity(groupOpacity)
                        .direction(FlexDirection.ROW)
                        .children(square(RED, 60), square(GREEN, 60).opacity(childOpacity)));
    }

    private void paint(Box root) {
        BoxPainter.paint(target.frame(), root);
        target.end();
    }

    @Nested
    @DisplayName("on a display that is not 1:1")
    class Scaled {

        /// A 60x60 logical square, faded so that it is promoted to a layer, in
        /// the top-left of a 200x200 logical frame rasterized at 2x.
        private static Box fadedSquare() {
            return Box.filled(BACKDROP)
                    .size(StyleLength.points(200), StyleLength.points(200))
                    .children(Box.of()
                            .opacity(0.5)
                            .children(square(RED, 60)));
        }

        @Test
        @DisplayName("a promoted subtree is composited at its own size, not the scale's square")
        void layerIsBlittedOneToOne() {
            // The bug this pins, reported from a 2x Mac: every disabled control
            // -- the only widgets with an opacity, so the only ones promoted --
            // came out twice as big.
            //
            // A layer is allocated in PHYSICAL pixels (60 logical at 2x is a
            // 120x120 raster, which is the point: a layer is a raster and
            // rasterizing it at logical size would throw the display's detail
            // away). The frame it is composited onto is in LOGICAL coordinates,
            // because its Blend2D context carries the scale. So blitting the
            // raster at a logical origin draws 120 raster pixels across 120
            // *logical* units -- 240 physical -- and the subtree is twice the
            // size it laid out at.
            //
            // At 1x the two spaces coincide and nothing is visibly wrong, which
            // is why every test and every Linux run missed it.
            var scaled = TestFrames.of(400, 400, 2.0f, 0);
            BoxPainter.paint(scaled.frame(), fadedSquare());
            scaled.end();

            // Physical coordinates. The square is 60 logical wide, so it ends at
            // physical 120 and physical 180 is well past it.
            var insideTheSquare = scaled.pixel(60, 60);
            var pastIt = scaled.pixel(180, 180);

            assertNotEquals(BACKDROP, insideTheSquare,
                    "the faded square should cover physical (60, 60)");
            assertEquals(BACKDROP, pastIt,
                    () -> "the layer was composited at twice its size: physical (180, 180)"
                            + " is 90 logical, well outside a 60-point square, and holds #"
                            + Integer.toHexString(pastIt));
        }

        @Test
        @DisplayName("and the same is true of a fractional scale")
        void fractionalScale() {
            // 1.5x rounds the raster up to 90x90 for a 60-point square, so the
            // blit cannot be a whole number of logical units either way -- which
            // is what makes this the case a "just divide by two" fix gets wrong.
            var scaled = TestFrames.of(300, 300, 1.5f, 0);
            BoxPainter.paint(scaled.frame(), fadedSquare());
            scaled.end();

            assertNotEquals(BACKDROP, scaled.pixel(45, 45),
                    "the faded square should cover physical (45, 45)");
            assertEquals(BACKDROP, scaled.pixel(120, 120),
                    "physical (120, 120) is 80 logical, outside a 60-point square");
        }
    }

    @Nested
    @DisplayName("group opacity")
    class GroupOpacity {

        @Test
        @DisplayName("the overlap shows the upper child only, not both faded")
        void overlapIsTheUpperChild() {
            // The assertion ADR-0064 asked for. Inside the overlap the green
            // square covered the red one *before* anything was faded, so what
            // reaches the frame is green at 50% over the backdrop — the same
            // colour as the part of the green square that overlaps nothing.
            //
            // Multiplying alpha per box instead, the overlap would be green at
            // 50% over red at 50% over the backdrop: a third colour, in neither
            // end state, and visibly redder.
            paint(overlapping(0.5));

            // The green square runs 30..90 across; the red one 0..60. So 45 is
            // inside the overlap and 75 is green over the backdrop alone.
            var inOverlap = target.pixel(45, 30);
            var greenOnly = target.pixel(75, 30);

            assertEquals(greenOnly, inOverlap,
                    () -> "the covered square showed through: overlap #"
                            + Integer.toHexString(inOverlap) + " against #"
                            + Integer.toHexString(greenOnly));
        }

        @Test
        @DisplayName("and the faded group is still faded")
        void stillFades() {
            // The other half: a layer that forgot its alpha would draw the group
            // at full strength and this test is what would catch it.
            paint(overlapping(0.5));

            var faded = target.pixel(75, 30);
            assertNotEquals(GREEN, faded, "the group was not faded at all");
            assertNotEquals(BACKDROP, faded, "or was faded out of existence");

            // Half of green over the backdrop, on each channel.
            var expectedRed = (((GREEN >>> 16) & 0xFF) + ((BACKDROP >>> 16) & 0xFF)) / 2;
            assertTrue(Math.abs(((faded >>> 16) & 0xFF) - expectedRed) <= 2,
                    () -> "expected about #" + Integer.toHexString(expectedRed)
                            + " of red, got #" + Integer.toHexString(faded));
        }

        @Test
        @DisplayName("a fully opaque group is not promoted and looks the same")
        void opaqueIsUnchanged() {
            // Promotion has to be invisible where it makes no difference, or
            // every existing golden would have moved.
            paint(overlapping(1.0));

            assertEquals(GREEN, target.pixel(45, 30), "the upper square, undimmed");
            assertEquals(RED, target.pixel(10, 30), "and the lower one where it shows");
        }

        @Test
        @DisplayName("a translucent leaf keeps the cheap path")
        void leavesAreNotPromoted() {
            // Stated policy: a leaf's own shapes can overlap each other, but the
            // difference is a fraction of a level on an antialiased edge, and
            // paying an allocation and a blit for every faded label to fix it
            // would be a poor trade.
            var tree = RenderTree.create();
            try {
                tree.update(target.frame(), Box.filled(BACKDROP)
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(square(RED, 60).opacity(0.5)));
                tree.paint(target.frame());
            } finally {
                tree.close();
                target.end();
            }

            // Half red over the backdrop, which is what both paths give for a
            // leaf — the test is that it draws at all and is faded.
            var faded = target.pixel(10, 10);
            assertNotEquals(RED, faded);
            assertNotEquals(BACKDROP, faded);
        }
    }

    @Nested
    @DisplayName("the raster is kept")
    class Caching {

        @Test
        @DisplayName("an unchanged promoted subtree keeps its layer between frames")
        void reusesTheRaster() {
            // The §1.7 reason for promotion. `hasChanged` is what decides whether
            // the raster is blitted again or drawn again, so it is what to assert
            // on: inferring it from pixels would pass either way.
            try (var tree = RenderTree.create()) {
                var box = overlapping(0.5);

                tree.update(target.frame(), box);
                tree.paint(target.frame());
                assertEquals(1, tree.layersRepainted(), "the first frame rasterizes it");

                tree.update(target.frame(), box);
                tree.paint(target.frame());
                assertEquals(0, tree.layersRepainted(),
                        "nothing changed, so the promoted subtree is a blit");
            } finally {
                target.end();
            }
        }

        @Test
        @DisplayName("a changed child invalidates the layer above it")
        void aChangedChildReachesTheLayer() {
            // The failure a naive implementation produces: the promoted node's
            // own box is identical frame to frame, so a check that looked only at
            // it would keep blitting a raster of the *old* children forever.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), overlapping(0.5));
                tree.paint(target.frame());
                tree.update(target.frame(), overlapping(0.5));
                tree.paint(target.frame());
                assertEquals(0, tree.layersRepainted(), "settled");

                var recoloured = Box.filled(BACKDROP)
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(Box.of()
                                .opacity(0.5)
                                .direction(FlexDirection.ROW)
                                .children(square(RED, 60), square(0xFF88C0D0, 60)));

                tree.update(target.frame(), recoloured);
                tree.paint(target.frame());
                assertEquals(1, tree.layersRepainted(),
                        "a child's colour changed and the layer above it did not notice");
            } finally {
                target.end();
            }
        }

        @Test
        @DisplayName("a fading group keeps its raster — the case promotion exists for")
        void fadingReusesTheRaster() {
            // §1.7's actual promise. The subtree looks the same; only the alpha
            // of the composite moves. The raster was drawn at full strength, so
            // it is still correct at every step of the fade, and a frame of the
            // transition is a blit.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), overlapping(0.5));
                tree.paint(target.frame());

                tree.update(target.frame(), overlapping(0.4));
                tree.paint(target.frame());
                assertEquals(1, tree.layersComposited(), "the group is still a layer");
                assertEquals(0, tree.layersRepainted(),
                        "an opacity transition rasterized the layer it exists to reuse");
                // And the screen does still differ, which is a separate question
                // and the one damage asks.
                assertTrue(tree.rootChanged(), "the group looks different, so it must be damaged");
            } finally {
                target.end();
            }
        }

        @Test
        @DisplayName("a moving group keeps its raster too")
        void movingReusesTheRaster() {
            // The transform is applied to the blit for the same reason the alpha
            // is, so the same reuse follows.
            try (var tree = RenderTree.create()) {
                // Both trees are `moved`, differing only in how far. Comparing
                // against `overlapping` would have varied the *children* too,
                // which correctly invalidates and would have made this pass or
                // fail for the wrong reason.
                tree.update(target.frame(), moved(0.5, 0));
                tree.paint(target.frame());

                tree.update(target.frame(), moved(0.5, 12));
                tree.paint(target.frame());
                assertEquals(0, tree.layersRepainted(),
                        "a transform on a promoted node should move the blit, not the raster");
            } finally {
                target.end();
            }
        }

        @Test
        @DisplayName("but a child's opacity is baked in, so it does invalidate")
        void aChildsOpacityIsInTheRaster() {
            // The asymmetry that makes this correct rather than merely faster: a
            // *descendant's* opacity is drawn into the raster, because only the
            // promoted node's own is deferred to the composite.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), childFaded(0.5, 1.0));
                tree.paint(target.frame());

                tree.update(target.frame(), childFaded(0.5, 0.3));
                tree.paint(target.frame());
                assertEquals(1, tree.layersRepainted(),
                        "a child faded and the raster above it did not notice");
            } finally {
                target.end();
            }
        }
    }

    @Nested
    @DisplayName("bounds")
    class Bounds {

        @Test
        @DisplayName("a child transformed outside its parent is not clipped away")
        void coversTransformedChildren() {
            // The bug a layer sized to the border box produces: the child is
            // drawn outside the raster and simply vanishes. It is why the bounds
            // walk maps all four corners of every descendant.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), Box.filled(BACKDROP)
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .children(Box.of()
                                .opacity(0.5)
                                .size(StyleLength.points(40), StyleLength.points(40))
                                .children(square(GREEN, 40).transform(Transform.of(
                                        new Transform.Function.Translate(
                                                Transform.Length.px(80),
                                                Transform.Length.px(80)))))));
                tree.paint(target.frame());
            } finally {
                target.end();
            }

            // The child was laid out at (0,0) and moved to (80,80). If the layer
            // had been the parent's own 40x40, nothing would be here.
            var moved = target.pixel(100, 100);
            assertNotEquals(BACKDROP, moved,
                    "the transformed child was clipped out of its parent's layer");
        }

        @Test
        @DisplayName("a focus ring outside the border box survives")
        void coversTheOutline() {
            // `outline` is drawn outside the box by design (ADR-0064), so a layer
            // sized to the border box would cut the ring in half.
            try (var tree = RenderTree.create()) {
                tree.update(target.frame(), Box.filled(BACKDROP)
                        .size(StyleLength.points(200), StyleLength.points(200))
                        .padding(StyleLength.points(20))
                        .children(Box.of()
                                .opacity(0.5)
                                .decoration(io.github.digitalsmile.goldberry.css.Decoration.NONE
                                        .outline(2, 0xFF88C0D0, 2))
                                .size(StyleLength.points(40), StyleLength.points(40))
                                .children(square(GREEN, 40))));
                tree.paint(target.frame());
            } finally {
                target.end();
            }

            // The ring sits 2px out from the box's edge at x=20, so around x=17.
            var ring = target.pixel(17, 40);
            assertNotEquals(BACKDROP, ring, "the focus ring was clipped by its own layer");
        }
    }
}
