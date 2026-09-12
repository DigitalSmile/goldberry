package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;

/// What a dashed stroke actually puts on the surface.
///
/// [io.github.digitalsmile.goldberry.paint.geom.DasherTest] checks the geometry
/// with no rasterizer under it; this checks the half that only pixels can answer,
/// and it exists because one of those answers is **not** what ADR-0278 first
/// assumed.
class DashRenderingTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 10;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// How many pixels of the middle row a stroke inked.
    private static int inked(Stroke stroke) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try {
            target.frame().fill(WHITE);
            target.frame().strokePath(Path.line(2, 5, 38, 5), stroke, BLACK);
        } finally {
            target.end();
        }
        var count = 0;
        for (var x = 0; x < WIDTH; x++) {
            if (target.pixel(x, 5) != WHITE) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("a dashed line inks less than a solid one, and more than nothing")
    void dashesLeaveGaps() {
        var solid = inked(Stroke.of(2));
        var dashed = inked(Stroke.of(2).dashed(4, 4));

        assertTrue(dashed > 0, "the dashes were drawn");
        assertTrue(dashed < solid, () -> "and they are gaps: " + dashed + " of " + solid);
    }

    @Test
    @DisplayName("a zero-length dash draws NOTHING, whatever the cap says")
    void zeroLengthDashesAreNotDots() {
        // **This is the correction to ADR-0278.** SVG says a zero-length dash is
        // rendered as a dot when the cap is round or square, and `Dasher` emits
        // exactly that — a sub-path whose two points coincide, asserted in
        // `DasherTest`. Blend2D's stroker then **drops it**: a zero-length
        // sub-path contributes no outline, and a round cap on nothing is nothing.
        //
        // So `stroke-dasharray="0 8"` — the idiom for a dotted line everywhere
        // else — comes out blank here. Written down as a test rather than as a
        // sentence, because the failure is a line that silently does not appear
        // and the next person to reach for it deserves to find this.
        assertEquals(0, inked(Stroke.round(4).dash(Dash.of(0, 8))), "nothing was drawn at all");
    }

    @Test
    @DisplayName("a dotted line is written with a short dash, not a zero-length one")
    void shortDashesAreTheDottedLine() {
        // The workaround, and it is a good one: a dash a fraction of the stroke
        // width, round-capped, is a dot to every eye and is drawn by every
        // rasterizer.
        assertTrue(inked(Stroke.round(4).dash(Dash.of(1, 7))) > 0, "a one-pixel dash is a dot that exists");
    }
}
