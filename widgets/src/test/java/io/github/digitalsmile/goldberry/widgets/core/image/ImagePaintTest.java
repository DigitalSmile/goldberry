package io.github.digitalsmile.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Limits;

/// How big an image's box is ([ADR-0358]): the picture's, until a stylesheet
/// says otherwise. A 400×200 natural size throughout.
class ImagePaintTest {

    private static Length[] size(Length width, Length height, double measured, ComputedStyle style) {
        return ImagePaint.intrinsic(width, height, 400, 200, measured, style);
    }

    private static Length[] points(double width, double height) {
        return new Length[] {Length.points((float) width), Length.points((float) height)};
    }

    @Test
    @DisplayName("with neither axis given, the natural size")
    void natural() {
        assertArrayEquals(points(400, 200), size(Length.AUTO, Length.UNDEFINED, 0, ComputedStyle.INITIAL));
    }

    @Test
    @DisplayName("a max-width shrinks both axes, so the picture is smaller rather than squashed")
    void cappedInProportion() {
        var style = ComputedStyle.INITIAL.limits(Limits.NONE.maxWidth(Length.points(100)));

        assertArrayEquals(points(100, 50), size(Length.AUTO, Length.AUTO, 0, style));
    }

    @Test
    @DisplayName("one axis in points, and the other follows the picture's shape")
    void oneAxis() {
        assertArrayEquals(points(300, 150), size(Length.points(300), Length.AUTO, 0, ComputedStyle.INITIAL));
        assertArrayEquals(points(80, 40), size(Length.AUTO, Length.points(40), 0, ComputedStyle.INITIAL));
    }

    @Test
    @DisplayName("a percentage width takes its height from the width last laid out, once there is one")
    void percentage() {
        var half = Length.percent(50);

        assertNull(size(half, Length.AUTO, 0, ComputedStyle.INITIAL), "nothing measured yet");
        assertArrayEquals(new Length[] {half, Length.points(60)}, size(half, Length.AUTO, 120, ComputedStyle.INITIAL));
    }

    @Test
    @DisplayName("with both axes given, the box is the stylesheet's")
    void both() {
        assertNull(size(Length.points(10), Length.points(10), 0, ComputedStyle.INITIAL));
    }
}
