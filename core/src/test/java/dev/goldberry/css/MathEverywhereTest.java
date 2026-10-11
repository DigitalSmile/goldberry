package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.cascade.Transitions.Animatable;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Transform;

/// The math functions in every reader that is not a box length: a transform's
/// lengths, angles and numbers, a time, a plain number, a weight, a gradient's
/// angle.
class MathEverywhereTest {

    private static final CssLength.Context CONTEXT = new CssLength.Context(10, 16).withViewport(1000, 500);

    private static ComputedStyle style(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "a { " + declarations + " }");
        return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(element("a")), CONTEXT);
    }

    private static List<Token> tokens(String text) {
        return CssTokenizer.tokenize(text).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }

    @Nested
    @DisplayName("transform")
    class Transforms {

        private Transform.Function only(String value) {
            var functions = style("transform: " + value).transform().functions();
            assertEquals(1, functions.size(), value);
            return functions.getFirst();
        }

        @Test
        @DisplayName("a translation may be a percentage of the box plus pixels")
        void percentPlusPixels() {
            var translate = (Transform.Function.Translate) only("translate(calc(-50% + 4px), 1vw)");
            assertEquals(Transform.Length.percentPlus(-50, 4), translate.x());
            assertEquals(-46, translate.x().resolve(100), 1e-9);
            assertEquals(Transform.Length.px(10), translate.y());
        }

        @Test
        @DisplayName("a nested function's commas are its own, not the transform's")
        void nestedCommas() {
            var translate = (Transform.Function.Translate) only("translate(min(4px, 2em), 3pt)");
            assertEquals(Transform.Length.px(4), translate.x());
            assertEquals(Transform.Length.px(4), translate.y());
        }

        @Test
        @DisplayName("an angle and a scale fold")
        void anglesAndNumbers() {
            var rotate = (Transform.Function.Rotate) only("rotate(calc(45deg * 2))");
            assertEquals(Math.PI / 2, rotate.radians(), 1e-9);
            var scale = (Transform.Function.Scale) only("scale(calc(1 / 2))");
            assertEquals(0.5, scale.x(), 1e-9);
        }

        @Test
        @DisplayName("a length where an angle goes drops the transform")
        void wrongKind() {
            assertFalse(ComputedStyle.applies("transform", tokens("rotate(calc(4px * 2))"), CONTEXT));
            assertFalse(ComputedStyle.applies("transform", tokens("scale(calc(1px))"), CONTEXT));
        }

        @Test
        @DisplayName("a percentage plus pixels moves between two such lengths")
        void mixes() {
            var from = Transform.of(
                    new Transform.Function.Translate(Transform.Length.percentPlus(0, 0), Transform.Length.ZERO));
            var to = Transform.of(
                    new Transform.Function.Translate(Transform.Length.percentPlus(100, 10), Transform.Length.ZERO));
            var half =
                    (Transform.Function.Translate) from.mix(to, 0.5).functions().getFirst();
            assertEquals(Transform.Length.percentPlus(50, 5), half.x());
            assertEquals("calc(50.0% + 5.0px)", half.x().toString());
            assertEquals(Transform.Length.px(3), new Transform.Length(1, false, 2));
        }
    }

    @Test
    @DisplayName("a time folds, and a duration of a length does not")
    void times() {
        var timing = style("transition: opacity calc(100ms * 2) linear round(up, 150ms, 1s)")
                .transitions()
                .byProperty()
                .get(Animatable.OPACITY);
        assertEquals(200, timing.durationMillis(), 1e-9);
        assertEquals(1000, timing.delayMillis(), 1e-9);
        assertFalse(ComputedStyle.applies("transition-duration", tokens("calc(2px)"), CONTEXT));
    }

    @Test
    @DisplayName("a plain number folds, and a percentage alone is a fraction")
    void numbers() {
        assertEquals(0.5, style("opacity: calc(1 / 2)").opacity(), 1e-9);
        assertEquals(0.25, style("opacity: calc(50% / 2)").opacity(), 1e-9);
        assertEquals(2, style("flex-grow: calc(1 + 1)").flexGrow(), 1e-9);
        assertEquals(3, style("flex: calc(1 + 2)").flexGrow(), 1e-9);
        assertEquals(500, style("font-weight: calc(400 + 100)").typography().weight());
        assertFalse(ComputedStyle.applies("opacity", tokens("calc(1px)"), CONTEXT));
    }

    @Test
    @DisplayName("a gradient's angle folds")
    void gradientAngle() {
        assertTrue(ComputedStyle.applies(
                "background", tokens("linear-gradient(calc(90deg + 0.25turn), red, blue)"), CONTEXT));
        assertFalse(ComputedStyle.applies("background", tokens("linear-gradient(calc(1px), red, blue)"), CONTEXT));
    }
}
