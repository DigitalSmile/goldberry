package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The four enclosed glyphs a `message` draws — that each one is a ring or a
/// triangle with something inside it, and that it stays inside its box.
///
/// Written because these are the first marks made of **two** drawings: an
/// outline stroked, then a dot filled, with the path reset in between. Three
/// things can go wrong there and none of them throws — the outline can be
/// skipped, the dot can land on top of the symbol it belongs under, or either
/// can spill outside the slot the stylesheet gave it. A golden image would show
/// all three and say only "the picture changed"; these say which.
///
/// The assertions are about **where ink is**, not about the shape being pretty:
/// that is what `MessageGoldenTest` in `:widgets` is for.
class EnclosedMarkTest {

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// The glyph's box, deliberately larger than the 20px a banner uses: at 20
    /// the ring's interior is a handful of pixels and a test asserting on it
    /// would be asserting on the antialiaser.
    private static final int SIZE = 40;

    /// The four kinds that draw an outline with a symbol inside it.
    private static final Box.Mark.Kind[] ENCLOSED = {
        Box.Mark.Kind.CIRCLE_INFO, Box.Mark.Kind.CIRCLE_CHECK,
        Box.Mark.Kind.CIRCLE_ALERT, Box.Mark.Kind.TRIANGLE_ALERT,
    };

    /// The empty margin round the glyph's box, so that "outside its slot" is a
    /// place a test can look at rather than something the frame clips away.
    private static final int MARGIN = 10;

    /// Paints one mark in a `SIZE`×`SIZE` slot with [#MARGIN] of nothing round
    /// it. Coordinates below are the frame's, so the slot runs from `MARGIN` to
    /// `MARGIN + SIZE`.
    private static TestFrames.Target paint(Box.Mark.Kind kind) {
        var target = TestFrames.of(SIZE + MARGIN * 2, SIZE + MARGIN * 2, 1f);
        var box = Box.of()
                .padding(StyleLength.points(MARGIN))
                .children(Box.of()
                        .mark(new Box.Mark(kind, 0xFFFFFFFF, 2))
                        .size(StyleLength.points(SIZE), StyleLength.points(SIZE)));
        BoxPainter.paint(target.frame(), box);
        // Ended before the pixels are read: a context that has not been ended may
        // still have work queued, and half of an antialiased ring is a flake.
        target.end();
        return target;
    }

    /// How much white is in a rectangle, as a fraction of its pixels.
    private static double inkIn(TestFrames.Target target, int x0, int y0, int x1, int y1) {
        var inked = 0;
        var total = 0;
        for (var y = y0; y < y1; y++) {
            for (var x = x0; x < x1; x++) {
                total++;
                if (target.alphaAt(x, y) > 0x40) {
                    inked++;
                }
            }
        }
        return (double) inked / total;
    }

    @ParameterizedTest
    @EnumSource(value = Box.Mark.Kind.class,
            names = {"CIRCLE_INFO", "CIRCLE_CHECK", "CIRCLE_ALERT", "TRIANGLE_ALERT"})
    @DisplayName("an enclosed glyph stays inside its slot")
    void enclosureFitsItsBox(Box.Mark.Kind kind) {
        var target = paint(kind);
        var end = MARGIN + SIZE;

        // The enclosure is inset by half the stroke for exactly this reason: a
        // stroke is centred on its path, so a ring drawn *on* the box's edge
        // would put half its width outside all the way round -- which in a
        // banner is a ring overlapping the words beside it. The row at `MARGIN`
        // itself is fair game and is partly covered, because the outer edge of
        // the stroke is meant to land exactly there.
        for (var i = 0; i < SIZE + MARGIN * 2; i++) {
            assertEquals(0, target.alphaAt(i, MARGIN - 1),
                    kind + " painted above its slot at " + i);
            assertEquals(0, target.alphaAt(i, end),
                    kind + " painted below its slot at " + i);
            assertEquals(0, target.alphaAt(MARGIN - 1, i),
                    kind + " painted left of its slot at " + i);
            assertEquals(0, target.alphaAt(end, i),
                    kind + " painted right of its slot at " + i);
        }
        assertTrue(inkIn(target, MARGIN, MARGIN, end, end) > 0.02,
                kind + " drew almost nothing");
    }

    @ParameterizedTest
    @EnumSource(value = Box.Mark.Kind.class,
            names = {"CIRCLE_INFO", "CIRCLE_CHECK", "CIRCLE_ALERT"})
    @DisplayName("a ring reaches both sides of its middle row")
    void theRingIsDrawn(Box.Mark.Kind kind) {
        var target = paint(kind);

        // A circle is at its widest across the middle, so its outline is the only
        // thing that can be within a few pixels of the left and right edges
        // there. This is the failure mode where the symbol is drawn and the
        // enclosure round it is not: the glyph still looks like something.
        var middle = MARGIN + SIZE / 2;
        assertTrue(inkIn(target, MARGIN, middle - 1, MARGIN + 6, middle + 1) > 0,
                kind + " has no outline on the left of its middle row");
        assertTrue(inkIn(target, MARGIN + SIZE - 6, middle - 1, MARGIN + SIZE, middle + 1) > 0,
                kind + " has no outline on the right of its middle row");
    }

    @Test
    @DisplayName("a triangle reaches both sides of its base and neither at its apex")
    void theTriangleIsDrawn() {
        var target = paint(Box.Mark.Kind.TRIANGLE_ALERT);

        // Where a circle is widest across the middle, a triangle is widest along
        // the bottom -- so the same question is asked of the row that answers it.
        var bottom = MARGIN + SIZE;
        assertTrue(inkIn(target, MARGIN, bottom - 5, MARGIN + 6, bottom) > 0,
                "no outline at the left of the triangle's base");
        assertTrue(inkIn(target, bottom - 6, bottom - 5, bottom, bottom) > 0,
                "no outline at the right of the triangle's base");
        // And nothing at all beside the apex, which is what makes it a triangle
        // rather than a rounded box: a circle's corners are empty too, but a
        // circle fills the middle of its top edge and a triangle does not.
        assertEquals(0.0, inkIn(target, MARGIN, MARGIN, MARGIN + 10, MARGIN + 6), 0.001,
                "the triangle's top-left corner should be empty");
    }

    @Test
    @DisplayName("a triangle is empty where a circle is not, which is the point of it")
    void theTriangleIsNotACircle() {
        // §1.2 forbids colour as the only carrier of meaning, and warning and
        // danger are the two kinds nobody may confuse. What tells them apart with
        // the hue removed is the *shape*, so this measures the difference rather
        // than trusting it: a triangle's top corners are empty and a circle's are
        // not, because a circle is at its widest where a triangle is at its
        // narrowest.
        var x0 = MARGIN + 2;
        var x1 = MARGIN + 10;
        var y0 = MARGIN + SIZE / 4;
        var y1 = MARGIN + SIZE / 2;
        var circle = inkIn(paint(Box.Mark.Kind.CIRCLE_ALERT), x0, y0, x1, y1);
        var triangle = inkIn(paint(Box.Mark.Kind.TRIANGLE_ALERT), x0, y0, x1, y1);

        assertTrue(circle > triangle,
                "a circle's upper left should carry more ink than a triangle's, and"
                        + " they measured " + circle + " and " + triangle);
    }

    @Test
    @DisplayName("an `i` has its dot above its stem, and a `!` has it below")
    void theDotIsWhereTheGlyphSaysItIs() {
        // The one pair a reader has to tell apart at 20 logical pixels: `info`
        // and `alert` are the same ring with the same two strokes in it, upside
        // down from each other. If the dot were centred -- the obvious mistake,
        // since the fill is drawn from the middle of the box -- they would be the
        // same drawing.
        var info = paint(Box.Mark.Kind.CIRCLE_INFO);
        var alert = paint(Box.Mark.Kind.CIRCLE_ALERT);

        var band = 3;
        var middle = MARGIN + SIZE / 2;
        var top = MARGIN + SIZE / 4;
        var bottom = MARGIN + 3 * SIZE / 4;
        // A narrow column down the middle, split above and below the centre.
        var infoAbove = inkIn(info, middle - band, top, middle + band, middle - 4);
        var infoBelow = inkIn(info, middle - band, middle + 4, middle + band, bottom);
        var alertAbove = inkIn(alert, middle - band, top, middle + band, middle - 4);
        var alertBelow = inkIn(alert, middle - band, middle + 4, middle + band, bottom);

        assertTrue(infoAbove > 0 && infoBelow > 0, "an `i` has ink above and below the centre");
        assertTrue(alertAbove > 0 && alertBelow > 0, "a `!` has ink above and below the centre");
        // The stem is longer than the dot, so whichever half holds the stem holds
        // more ink -- and the two kinds hold it in opposite halves.
        assertTrue(infoBelow > infoAbove,
                "an `i`'s stem is below its dot, and the halves measured "
                        + infoBelow + " below against " + infoAbove + " above");
        assertTrue(alertAbove > alertBelow,
                "a `!`'s bar is above its dot, and the halves measured "
                        + alertAbove + " above against " + alertBelow + " below");
    }

    @Test
    @DisplayName("a ring is hollow between its outline and its symbol")
    void theRingIsNotFilled() {
        // The failure this catches is a `fillPath` where a `strokePath` was
        // meant: a filled circle is a perfectly plausible-looking blob at 20px
        // and says nothing at all.
        var target = paint(Box.Mark.Kind.CIRCLE_CHECK);

        // Between the ring and the tick, on the row through the middle: the tick
        // occupies the centre, so this looks just inside the left of the ring.
        var middle = MARGIN + SIZE / 2;
        var gap = inkIn(target, MARGIN + 8, middle - 1, MARGIN + 13, middle + 1);
        assertEquals(0.0, gap, 0.001, "the inside of the ring should be empty, and was " + gap);
    }
}
