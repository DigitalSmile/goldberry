package io.github.digitalsmile.goldberry.paint.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.Box;

/// What one box draws outside itself, which is the only thing between a culled
/// subtree and a focus ring that vanishes at a viewport's edge.
@DisplayName("BoxInk")
class BoxInkTest {

    private static final int OPAQUE = 0xFF112233;

    @Test
    @DisplayName("an ordinary box draws exactly its own rectangle")
    void plain() {
        assertEquals(new Ink(0, 0, 80, 24), BoxInk.of(Box.filled(OPAQUE), 80, 24));
    }

    @Test
    @DisplayName("a focus ring reaches out by its offset and its width, on all four sides")
    void ring() {
        var focused = Box.filled(OPAQUE).decoration(new Decoration(Corners.SQUARE, 0, 0, 2, OPAQUE, 2, Shadow.NONE));

        assertEquals(new Ink(-4, -4, 84, 28), BoxInk.of(focused, 80, 24));
    }

    @Test
    @DisplayName("a ring in a transparent colour reaches nowhere, because it draws nothing")
    void invisibleRing() {
        var unfocused =
                Box.filled(OPAQUE).decoration(new Decoration(Corners.SQUARE, 0, 0, 2, 0x00000000, 2, Shadow.NONE));

        assertEquals(new Ink(0, 0, 80, 24), BoxInk.of(unfocused, 80, 24));
    }

    /// The asymmetry is the point: `0 8px 32px` reaches 24px below the box and
    /// 8px above it, and one outset for four sides is either a waste on three of
    /// them or — the day a shadow is offset further than it is blurred — a miss
    /// on one (ADR-0310).
    @Test
    @DisplayName("a drop shadow reaches further below than above, and is measured per side")
    void shadowIsAsymmetric() {
        var raised = Box.filled(OPAQUE)
                .decoration(new Decoration(Corners.SQUARE, 0, 0, 0, 0, 0, new Shadow(0, 8, 32, 0, 0x80000000)));

        var ink = BoxInk.of(raised, 80, 24);

        assertEquals(-8, ink.top(), 1e-9, "the blur's half, less the offset");
        assertEquals(24 + 24, ink.bottom(), 1e-9, "the blur's half, plus the offset");
        assertEquals(-16, ink.left(), 1e-9);
        assertEquals(80 + 16, ink.right(), 1e-9);
    }

    @Test
    @DisplayName("the ring and the shadow take the larger of the two per side, not their sum")
    void ringAndShadowDoNotStack() {
        var both = Box.filled(OPAQUE)
                .decoration(new Decoration(Corners.SQUARE, 0, 0, 2, OPAQUE, 2, new Shadow(0, 8, 32, 0, 0x80000000)));

        var ink = BoxInk.of(both, 80, 24);

        assertEquals(-16, ink.left(), 1e-9, "the shadow reaches further than the ring");
        assertEquals(-8, ink.top(), 1e-9, "and still does above, where it reaches least");
    }

    /// `item-lead` is 16 square because a menu's leading column has to be one
    /// width whether it holds a tick or an icon, and the icons applications put
    /// in it are 20 (ADR-0143). The painter centres the glyph, so it hangs 2px
    /// out on every side of a slot that never grew.
    @Test
    @DisplayName("an icon bigger than its slot hangs out of it, because the painter centres it")
    void oversizedIconOverhangs() {
        try (var glyph = Icon.bundled("check", 20)) {
            var slot = Box.icon(glyph, OPAQUE);

            assertEquals(new Ink(-2, -2, 18, 18), BoxInk.of(slot, 16, 16));
            assertEquals(new Ink(0, 0, 20, 20), BoxInk.of(slot, 20, 20), "and not at all when it fits");
            assertEquals(new Ink(0, 0, 40, 40), BoxInk.of(slot, 40, 40), "nor when the slot is roomier");
        }
    }
}
