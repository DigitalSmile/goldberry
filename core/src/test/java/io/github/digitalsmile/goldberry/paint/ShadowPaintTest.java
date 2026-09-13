package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;

/// `box-shadow`, in pixels — ADR-0310.
///
/// [io.github.digitalsmile.goldberry.paint.shadow.ShadowRampTest] checks the
/// alphas and
/// [io.github.digitalsmile.goldberry.paint.shadow.ShadowGeometryTest] the shapes;
/// this checks that both of them reach the rasterizer, in the order that makes a
/// shadow a shadow. The bug it pins is the one that has no wrong number anywhere
/// upstream: a shadow painted *after* the background is a dark rectangle over the
/// control.
class ShadowPaintTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int RED = 0xFFFF0000;

    private static final int BLACK = 0xFF000000;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A white page with one 60x40 box centred in it at (30, 30), carrying
    /// `shadow`.
    ///
    /// Through a padded parent rather than by placing the box by hand, so the
    /// shadow is drawn where the layout put the box and not where a test thought
    /// it would be.
    private static TestFrames.Target painted(Shadow shadow, int background, Corners corners) {
        var target = TestFrames.of(120, 100, 1f);
        var box = Box.filled(background)
                .size(Length.points(60), Length.points(40))
                .decoration(Decoration.NONE.corners(corners).shadow(shadow));
        BoxPainter.paint(
                target.frame(),
                Box.filled(WHITE).padding(Insets.all(Length.points(30))).children(box));
        return target;
    }

    @Test
    @DisplayName("a shadow darkens the page below the box and leaves the box itself alone")
    void castsDown() {
        // `0 4px 12px`: reaches 10px below the box's bottom edge at y=70, and 2px
        // above its top edge at y=30.
        var target = painted(new Shadow(0, 4, 12, 0, BLACK), RED, Corners.SQUARE);

        assertEquals(RED, target.pixel(60, 50), "the box is untouched");
        assertTrue(luminance(target.pixel(60, 74)) < 250, "the page below the box is darkened");
        assertEquals(WHITE, target.pixel(60, 5), "and the page well above it is not");
    }

    @Test
    @DisplayName("the fade is a fade: further from the box is lighter")
    void fades() {
        var target = painted(new Shadow(0, 4, 16, 0, BLACK), RED, Corners.SQUARE);

        // Three samples walking away from the bottom edge, inside the 12px the
        // shadow reaches. A ramp read straight off the curve — the mistake
        // `ShadowRamp` exists to avoid — is monotonic too, so this is the weaker
        // half of the claim and the unit test is the stronger one. What it
        // catches is the bands arriving in the wrong order.
        var near = luminance(target.pixel(60, 72));
        var middle = luminance(target.pixel(60, 76));
        var far = luminance(target.pixel(60, 80));

        assertTrue(near < middle, "the shadow is darkest against the box");
        assertTrue(middle < far, "and lighter further out");
        assertTrue(far < 255, "but still there at 10px");
    }

    @Test
    @DisplayName("a shadow is under the box, not over it")
    void underneath() {
        // The paint order, which is the whole of what "drop shadow" means. A
        // shadow drawn after the background is a dark rectangle over the control
        // and every number upstream of it is still correct.
        var target = painted(new Shadow(0, 0, 0, 20, BLACK), RED, Corners.SQUARE);

        assertEquals(RED, target.pixel(60, 50), "the middle of the box");
        assertEquals(RED, target.pixel(31, 31), "and its top-left corner");
        assertEquals(BLACK, target.pixel(60, 85), "with the hard shadow spread out below it");
    }

    @Test
    @DisplayName("an offset shadow is cast to one side and not the other")
    void asymmetric() {
        var target = painted(new Shadow(0, 10, 0, 0, BLACK), RED, Corners.SQUARE);

        assertEquals(BLACK, target.pixel(60, 75), "10px below the box");
        assertEquals(WHITE, target.pixel(60, 25), "and nothing above it");
    }

    @Test
    @DisplayName("a rounded box casts a rounded shadow")
    void rounded() {
        // Concentric with the box, so a hard shadow spread 8px out has a 20px
        // radius where the box has 12 — and its own corner is still empty.
        var target = painted(new Shadow(0, 0, 0, 8, BLACK), RED, Corners.all(12));

        assertEquals(WHITE, target.pixel(22, 22), "the shadow's corner is rounded away, so the page shows");
        assertEquals(BLACK, target.pixel(60, 23), "and its top edge is solid");
    }

    @Test
    @DisplayName("a transparent shadow paints nothing at all")
    void transparent() {
        var target = painted(new Shadow(0, 8, 24, 8, 0x00000000), RED, Corners.SQUARE);

        assertEquals(WHITE, target.pixel(60, 80));
        assertEquals(WHITE, target.pixel(5, 50));
    }

    @Test
    @DisplayName("a translucent box shows its own shadow through itself")
    void throughATranslucentBox() {
        // Not CSS's behaviour, and it is written down rather than hidden: CSS
        // knocks the border box out of an outer shadow, and the rasterizer
        // binding has neither a path clip nor a fill rule to cut that hole with
        // (see `ShadowGeometry`). What the toolkit does instead is paint the
        // shadow under the box, so a half-transparent box is slightly darker
        // than its colour alone. Pinned here so that the day a fill rule lands,
        // this test is what says the deviation is gone.
        var target = painted(new Shadow(0, 0, 0, 0, BLACK), 0x80FF0000, Corners.SQUARE);

        assertTrue(luminance(target.pixel(60, 50)) < 128, "the shadow is still under the box");
    }

    private static double luminance(int argb) {
        return 0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF);
    }
}
