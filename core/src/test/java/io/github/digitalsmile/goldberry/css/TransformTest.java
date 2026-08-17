package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The matrix, the property, and the arithmetic between them — ADR-0068.
class TransformTest {

    /// Two matrices agree to within what a `double` round trip costs.
    private static void assertMatches(Affine expected, Affine actual) {
        assertEquals(expected.a(), actual.a(), 1e-9, "a");
        assertEquals(expected.b(), actual.b(), 1e-9, "b");
        assertEquals(expected.c(), actual.c(), 1e-9, "c");
        assertEquals(expected.d(), actual.d(), 1e-9, "d");
        assertEquals(expected.e(), actual.e(), 1e-9, "e");
        assertEquals(expected.f(), actual.f(), 1e-9, "f");
    }

    private static void assertMaps(Affine matrix, double x, double y, double toX, double toY) {
        assertEquals(toX, matrix.mapX(x, y), 1e-9,
                "(" + x + ", " + y + ") should land at x=" + toX);
        assertEquals(toY, matrix.mapY(x, y), 1e-9,
                "(" + x + ", " + y + ") should land at y=" + toY);
    }

    /// The whole cascade, so a `transform` declaration is tested through the
    /// machinery that will actually deliver it rather than through the parser
    /// alone.
    private static ComputedStyle compute(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT);
    }

    @Nested
    @DisplayName("the matrix")
    class TheMatrix {

        @Test
        @DisplayName("the identity moves nothing")
        void identity() {
            assertTrue(Affine.IDENTITY.isIdentity());
            assertMaps(Affine.IDENTITY, 3, 7, 3, 7);
        }

        @Test
        @DisplayName("rotate turns the top edge toward the right, as CSS does")
        void rotationIsClockwise() {
            // y grows downward, so a positive angle is clockwise on screen. A
            // matrix that got this backwards would still look like "a rotation"
            // in every screenshot taken of a symmetric shape, which is why the
            // assertion is on a point rather than on the entries.
            var quarter = Affine.rotate(Math.toRadians(90));
            assertMaps(quarter, 1, 0, 0, 1);
        }

        @Test
        @DisplayName("`then` composes in reading order")
        void compositionOrder() {
            // Scale first, then move: the point doubles to (2, 0) and then
            // shifts by ten. The other order would give (11, 0)*... which is the
            // single most common way to get a transform stack wrong.
            var scaleThenMove = Affine.scale(2, 2).then(Affine.translate(10, 0));
            assertMaps(scaleThenMove, 1, 0, 12, 0);

            var moveThenScale = Affine.translate(10, 0).then(Affine.scale(2, 2));
            assertMaps(moveThenScale, 1, 0, 22, 0);
        }

        @Test
        @DisplayName("`about` applies a transform around a point")
        void aboutAPoint() {
            // A box 100 wide scaled by two about its own middle keeps its middle
            // where it was and grows 50 in each direction.
            var scaled = Affine.scale(2, 2).about(50, 0);
            assertMaps(scaled, 50, 0, 50, 0);
            assertMaps(scaled, 0, 0, -50, 0);
            assertMaps(scaled, 100, 0, 150, 0);
        }

        @Test
        @DisplayName("the inverse undoes the transform, whatever it is")
        void inverse() {
            var gnarly = Affine.scale(1.5, 0.5)
                    .then(Affine.rotate(Math.toRadians(37)))
                    .then(Affine.skew(Math.toRadians(12), Math.toRadians(-5)))
                    .then(Affine.translate(19, -4));

            var inverse = gnarly.invert();
            assertNotNull(inverse);
            // Round trip a point rather than compare entries: this is exactly
            // what hit testing does with it, so it is what should be asserted.
            var x = gnarly.mapX(13, 29);
            var y = gnarly.mapY(13, 29);
            assertEquals(13, inverse.mapX(x, y), 1e-9);
            assertEquals(29, inverse.mapY(x, y), 1e-9);
        }

        @Test
        @DisplayName("a collapsed transform has no inverse, and says so")
        void singular() {
            // Every point in the plane maps onto the same line, so there is no
            // question "which point did this come from" to answer. Hit testing
            // drops the region rather than matching everywhere.
            assertNull(Affine.scale(0, 1).invert());
            assertNull(Affine.scale(1, 0).invert());
            assertFalse(Affine.scale(0, 0).isInvertible());
            assertTrue(Affine.scale(1e-3, 1e-3).isInvertible(), "small is not singular");
        }

        @Test
        @DisplayName("decompose and recompose round-trips")
        void decomposition() {
            var original = Affine.scale(1.5, 0.5)
                    .then(Affine.skew(Math.toRadians(12), 0))
                    .then(Affine.rotate(Math.toRadians(37)))
                    .then(Affine.translate(19, -4));

            var parts = original.decompose();
            assertNotNull(parts);
            assertMatches(original, parts.recompose());
        }

        @Test
        @DisplayName("a reflection round-trips instead of coming back as a rotation")
        void decomposesAReflection() {
            // The case that catches a decomposition which does not fold the
            // negative determinant into one axis: it comes back as a rotation by
            // pi and a positive scale, which draws the same shape mirrored the
            // wrong way.
            var flipped = Affine.scale(-1, 1).then(Affine.rotate(Math.toRadians(20)));
            var parts = flipped.decompose();
            assertNotNull(parts);
            assertTrue(parts.scaleX() < 0, "the flip is attributed to the x axis");
            assertMatches(flipped, parts.recompose());
        }

        @Test
        @DisplayName("halfway between 0 and 180 degrees is a rotation, not a collapse")
        void interpolatesThroughARotation() {
            // Entry-by-entry interpolation gives all zeroes here — a box scaled
            // to a point — which is the reason `decompose` exists at all.
            var halfway = Affine.IDENTITY.mix(Affine.rotate(Math.PI), 0.5);
            assertEquals(1, Math.abs(halfway.determinant()), 1e-9,
                    "area is preserved through the whole turn");
            assertMaps(halfway, 1, 0, 0, 1);
        }
    }

    @Nested
    @DisplayName("resolving against a box")
    class ResolvingAgainstABox {

        @Test
        @DisplayName("no transform resolves to the identity and allocates nothing")
        void none() {
            assertTrue(Transform.NONE.isNone());
            assertSame(Affine.IDENTITY, Transform.NONE.matrix(100, 40));
        }

        @Test
        @DisplayName("a percentage translate is of the box's own size")
        void percentageTranslate() {
            var transform = Transform.of(
                    new Transform.Function.Translate(
                            Transform.Length.percent(50), Transform.Length.percent(25)));
            // 50% of 200 and 25% of 40. The default origin cancels out for a pure
            // translation, which is why this can be asserted directly.
            assertMaps(transform.matrix(200, 40), 0, 0, 100, 10);
        }

        @Test
        @DisplayName("the default origin is the box's middle")
        void defaultOrigin() {
            // A 100x40 box scaled by two: with `50% 50%` the centre stays put.
            var scaled = Transform.of(new Transform.Function.Scale(2, 2)).matrix(100, 40);
            assertMaps(scaled, 50, 20, 50, 20);
            assertMaps(scaled, 0, 0, -50, -20);
        }

        @Test
        @DisplayName("a top-left origin scales from the corner")
        void cornerOrigin() {
            var scaled = Transform.of(new Transform.Function.Scale(2, 2))
                    .origin(Transform.Origin.TOP_LEFT)
                    .matrix(100, 40);
            assertMaps(scaled, 0, 0, 0, 0);
            assertMaps(scaled, 100, 40, 200, 80);
        }

        @Test
        @DisplayName("the last function in the list is applied first")
        void listOrder() {
            // CSS: `transform: translate(10px) scale(2)` scales and then moves.
            // The box is 0-sized so the origin does not enter into it.
            var transform = Transform.of(
                    new Transform.Function.Translate(Transform.Length.px(10), Transform.Length.ZERO),
                    new Transform.Function.Scale(2, 2));
            assertMaps(transform.matrix(0, 0), 1, 0, 12, 0);
        }
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        @DisplayName("the functions of the 2D subset")
        void functions() {
            assertEquals(
                    List.of(new Transform.Function.Translate(
                            Transform.Length.px(10), Transform.Length.px(-4))),
                    compute("button { transform: translate(10px, -4px) }").transform().functions());

            assertEquals(
                    List.of(new Transform.Function.Scale(0.6, 0.6)),
                    compute("button { transform: scale(0.6) }").transform().functions());

            assertEquals(
                    List.of(new Transform.Function.Scale(1, 2)),
                    compute("button { transform: scaleY(2) }").transform().functions());

            assertEquals(
                    List.of(new Transform.Function.Translate(
                            Transform.Length.ZERO, Transform.Length.px(3))),
                    compute("button { transform: translateY(3px) }").transform().functions());
        }

        @Test
        @DisplayName("all four of CSS's angle units")
        void angles() {
            for (var spelling : List.of("90deg", "1.5707963267948966rad", "100grad", "0.25turn")) {
                var functions = compute("button { transform: rotate(" + spelling + ") }")
                        .transform().functions();
                assertEquals(1, functions.size(), spelling);
                var rotate = (Transform.Function.Rotate) functions.getFirst();
                assertEquals(Math.PI / 2, rotate.radians(), 1e-9, spelling);
            }
        }

        @Test
        @DisplayName("`transform: none` is a value, not a failure to parse")
        void none() {
            // It has to clear a transform an earlier rule set, which a dropped
            // declaration would not do.
            var style = compute("button { transform: scale(2) } button { transform: none }");
            assertTrue(style.transform().isNone());
        }

        @Test
        @DisplayName("a transform that will not parse is dropped, not half-applied")
        void dropped() {
            // Half of this is valid. Taking the valid half would move the box
            // somewhere nobody wrote, which is worse than not moving it.
            assertTrue(compute("button { transform: scale(2) rotate(bananas) }")
                    .transform().isNone());
            assertTrue(compute("button { transform: translate(10) }").transform().isNone(),
                    "a unitless non-zero length is not a length");
            assertTrue(compute("button { transform: perspective(400px) }").transform().isNone(),
                    "3D is not in the subset");
        }

        @Test
        @DisplayName("transform-origin takes lengths, percentages and keywords")
        void origins() {
            assertEquals(Transform.Origin.CENTER,
                    compute("button { transform-origin: center }").transform().origin());
            assertEquals(Transform.Origin.TOP_LEFT,
                    compute("button { transform-origin: left top }").transform().origin());
            assertEquals(new Transform.Origin(Transform.Length.px(4), Transform.Length.percent(100)),
                    compute("button { transform-origin: 4px bottom }").transform().origin());
        }

        @Test
        @DisplayName("`top left` is as valid as `left top`")
        void originKeywordsCommute() {
            assertEquals(Transform.Origin.TOP_LEFT,
                    compute("button { transform-origin: top left }").transform().origin());
        }

        @Test
        @DisplayName("a lone vertical keyword centres the other axis")
        void loneVerticalKeyword() {
            assertEquals(new Transform.Origin(Transform.Length.HALF, Transform.Length.ZERO),
                    compute("button { transform-origin: top }").transform().origin());
        }

        @Test
        @DisplayName("the origin survives whichever declaration comes first")
        void originAndTransformCommute() {
            var originFirst = compute(
                    "button { transform-origin: left top; transform: scale(2) }");
            var transformFirst = compute(
                    "button { transform: scale(2); transform-origin: left top }");

            assertEquals(Transform.Origin.TOP_LEFT, originFirst.transform().origin());
            assertEquals(Transform.Origin.TOP_LEFT, transformFirst.transform().origin());
            assertEquals(originFirst.transform(), transformFirst.transform());
        }

        @Test
        @DisplayName("a transform reaches the box through `style`")
        void reachesTheBox() {
            var style = compute("button { transform: rotate(45deg) }");
            var box = io.github.digitalsmile.goldberry.layout.Box.of().style(style);
            assertEquals(style.transform(), box.transform());
        }
    }

    @Nested
    @DisplayName("interpolation")
    class Interpolation {

        @Test
        @DisplayName("matching lists interpolate the numbers an author wrote")
        void componentwise() {
            var from = Transform.of(new Transform.Function.Scale(0.6, 0.6));
            var to = Transform.of(new Transform.Function.Scale(1, 1));

            assertEquals(
                    List.of(new Transform.Function.Scale(0.8, 0.8)),
                    from.mix(to, 0.5).functions());
        }

        @Test
        @DisplayName("`none` grows out of the identity of whatever it is becoming")
        void fromNone() {
            // The transition every `:hover` rule writes: the resting state has no
            // transform at all, so there is nothing to interpolate *from* unless
            // the missing function is filled in as its own identity. Padding with
            // a bare identity matrix instead would make a scale start at zero and
            // the control would appear out of a point.
            var halfway = Transform.NONE.mix(
                    Transform.of(new Transform.Function.Scale(1.2, 1.2)), 0.5);
            assertEquals(List.of(new Transform.Function.Scale(1.1, 1.1)), halfway.functions());
        }

        @Test
        @DisplayName("a rotation interpolates as an angle")
        void rotation() {
            var halfway = Transform.of(new Transform.Function.Rotate(0))
                    .mix(Transform.of(new Transform.Function.Rotate(Math.PI)), 0.5);
            var rotate = (Transform.Function.Rotate) halfway.functions().getFirst();
            assertEquals(Math.PI / 2, rotate.radians(), 1e-9);
        }

        @Test
        @DisplayName("lists of different shapes swap rather than deform")
        void incompatible() {
            // CSS would multiply both out and decompose; that needs a box to
            // resolve percentages against and this runs before layout. A jump is
            // the stated behaviour, so it is asserted rather than left to chance.
            var from = Transform.of(new Transform.Function.Scale(2, 2));
            var to = Transform.of(new Transform.Function.Rotate(1));

            assertEquals(from, from.mix(to, 0.4));
            assertEquals(to, from.mix(to, 0.6));
        }

        @Test
        @DisplayName("the endpoints are exact")
        void endpoints() {
            var from = Transform.of(new Transform.Function.Scale(0.6, 0.6));
            var to = Transform.of(new Transform.Function.Scale(1, 1));

            assertSame(from, from.mix(to, 0));
            assertSame(to, from.mix(to, 1));
        }
    }
}
