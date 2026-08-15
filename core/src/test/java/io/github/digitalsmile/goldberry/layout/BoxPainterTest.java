package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Yoga's layout arriving in Blend2D's pixels.
///
/// Each engine is tested on its own — a layout pass in `:natives`, a fill in
/// `BlendPaintTest`. What is only checkable here is that the numbers one
/// produces are the numbers the other consumes: an off-by-one in the walk, a
/// parent-relative position mistaken for an absolute one, or a scale applied
/// twice all produce a frame that renders and is wrong.
class BoxPainterTest {

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a row of two grown boxes paints two halves")
    void twoGrownBoxesSplitTheFrame() {
        var target = TestFrames.of(200, 100, 1f);
        var root = Box.of()
                .direction(FlexDirection.ROW)
                .children(
                        Box.filled(0xFFFF0000).grow(1),
                        Box.filled(0xFF0000FF).grow(1));

        BoxPainter.paint(target.frame(), root);

        assertEquals(0xFFFF0000, target.pixel(50, 50), "left half is red");
        assertEquals(0xFF0000FF, target.pixel(150, 50), "right half is blue");
        // The seam: the last pixel of the left half and the first of the right.
        assertEquals(0xFFFF0000, target.pixel(99, 50), "left half ends at 99");
        assertEquals(0xFF0000FF, target.pixel(100, 50), "right half starts at 100");
    }

    @Test
    @DisplayName("a child is positioned absolutely, not relative to its parent")
    void nestedBoxesAccumulatePosition() {
        // Yoga reports every box relative to its parent. Painting one at its
        // parent-relative position puts every nested box in the wrong place —
        // and shallow trees hide it, because the outermost parent is at 0,0.
        var target = TestFrames.of(100, 100, 1f);
        var root = Box.of()
                .padding(StyleLength.points(10))
                .children(
                        Box.of()
                                .grow(1)
                                .padding(StyleLength.points(10))
                                .children(Box.filled(0xFF00FF00).grow(1)));

        BoxPainter.paint(target.frame(), root);

        // 10 of padding, then 10 more: the green box starts at 20, not at 10.
        assertEquals(0, target.pixel(15, 15) >>> 24, "nothing painted at 15,15");
        assertEquals(0xFF00FF00, target.pixel(25, 25), "the green box starts at 20");
    }

    @Test
    @DisplayName("the walk visits every box exactly once, in tree order")
    void everyBoxIsVisitedOnce() {
        var target = TestFrames.of(100, 100, 1f);
        var root = Box.of()
                .direction(FlexDirection.ROW)
                .children(
                        Box.filled(0xFF111111).grow(1).children(Box.filled(0xFF222222).grow(1)),
                        Box.filled(0xFF333333).grow(1));

        var seen = new ArrayList<Integer>();
        BoxPainter.forEachBox(target.frame(), root, (box, layout) -> seen.add(box.background()));

        // Depth-first, parent before children. An index that slipped would show
        // up as a colour out of place or a box visited twice.
        assertEquals(
                java.util.List.of(Box.TRANSPARENT, 0xFF111111, 0xFF222222, 0xFF333333),
                seen);
    }

    @Test
    @DisplayName("a fully transparent box lays out but paints nothing")
    void transparentBoxesAreNotPainted() {
        var target = TestFrames.of(50, 50, 1f);

        BoxPainter.paint(target.frame(), Box.of().children(Box.of().grow(1)));

        assertEquals(0, target.pixel(25, 25), "the buffer was never touched");
    }

    @Test
    @DisplayName("layout is in logical coordinates and paint lands in physical ones")
    void scaleAppliesOnceAndOnlyOnce() {
        // The frame is 200x200 physical at 2x, so 100x100 logical. A box of 50
        // logical points must cover 100 physical pixels — once, not twice: a
        // scale applied in both the layout and the paint would give 200.
        var target = TestFrames.of(200, 200, 2f);
        var root = Box.of().children(
                Box.filled(0xFFFFFFFF).size(StyleLength.points(50), StyleLength.points(50)));

        BoxPainter.paint(target.frame(), root);

        assertEquals(0xFFFFFFFF, target.pixel(99, 99), "covered out to physical 99");
        assertEquals(0, target.pixel(100, 100) >>> 24, "and not beyond it");
    }

    @Test
    @DisplayName("a fractional scale still lands the far edge on the frame")
    void fractionalScalesTile() {
        // 150 physical at 1.5x is 100 logical. Two boxes growing equally are 50
        // logical each — 75 physical — and the pair has to cover the frame with
        // no seam of background between them.
        var target = TestFrames.of(150, 100, 1.5f);
        var root = Box.of()
                .direction(FlexDirection.ROW)
                .children(Box.filled(0xFFFF0000).grow(1), Box.filled(0xFF0000FF).grow(1));

        BoxPainter.paint(target.frame(), root);

        for (var x = 0; x < 150; x++) {
            final var column = x;
            final var alpha = target.pixel(x, 50) >>> 24;
            assertTrue(alpha == 0xFF, () -> "a gap at x=" + column + " (alpha " + alpha + ")");
        }
    }

    @Test
    @DisplayName("gap and padding reach the painted result")
    void gapAndPaddingAreHonoured() {
        // 40 tall, not 20: 10 of padding top and bottom would leave a content
        // box of no height at all, and every child would paint nothing.
        var target = TestFrames.of(100, 40, 1f);
        var root = Box.of()
                .direction(FlexDirection.ROW)
                .padding(StyleLength.points(10))
                .gap(StyleLength.points(20))
                .children(Box.filled(0xFFFFFFFF).grow(1), Box.filled(0xFFFFFFFF).grow(1));

        BoxPainter.paint(target.frame(), root);

        // 100 wide, 10 of padding each side, 20 of gap: two boxes of 30.
        assertEquals(0, target.pixel(5, 20) >>> 24, "left padding is empty");
        assertEquals(0xFFFFFFFF, target.pixel(20, 20), "first box");
        assertEquals(0, target.pixel(50, 20) >>> 24, "the gap is empty");
        assertEquals(0xFFFFFFFF, target.pixel(80, 20), "second box");
        assertEquals(0, target.pixel(95, 20) >>> 24, "right padding is empty");
    }
}
