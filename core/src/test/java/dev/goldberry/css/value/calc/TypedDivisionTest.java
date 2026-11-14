package dev.goldberry.css.value.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Transform;

/// CSS Values 4's typed arithmetic for division: a dimension over a dimension
/// of the same kind is a number, which is how `scale` follows the window
/// without a ladder of media queries.
@DisplayName("typed division")
class TypedDivisionTest {

    private static final CssLength.Context WINDOW = new CssLength.Context(10, 16).withViewport(1600, 900);

    private static @Nullable Quantity fold(String expression) {
        return Maths.fold(tokens(expression), WINDOW);
    }

    private static List<Token> tokens(String text) {
        return CssTokenizer.tokenize(text).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }

    @Test
    @DisplayName("a length over a length is a number")
    void lengthOverLength() {
        var ratio = fold("calc(100vw / 2560px)");
        assertNotNull(ratio);
        assertEquals(Quantity.Kind.NUMBER, ratio.kind());
        assertEquals(1600.0 / 2560, ratio.value(), 1e-12);
        assertEquals(Quantity.number(2), fold("calc(4em / 20px)"), "em is the context's 10px");
    }

    @Test
    @DisplayName("the HUD's scale is the smaller of the window's two ratios")
    void hudScale() {
        var scale = fold("min(100vw / 2560px, 100vh / 1440px)");
        assertNotNull(scale);
        assertEquals(Math.min(1600.0 / 2560, 900.0 / 1440), scale.value(), 1e-12);
    }

    @Test
    @DisplayName("angles and times cancel too, and what is left keeps its kind")
    void otherKinds() {
        assertEquals(Quantity.number(0.5), fold("calc(90deg / 180deg)"));
        assertEquals(Quantity.number(2), fold("calc(1s / 500ms)"));
        assertEquals(Quantity.pixels(30), fold("calc(10px * 6px / 2px)"), "two lengths over one is a length");
        assertEquals(Quantity.pixels(4), fold("calc(8px / 2)"), "a number divisor scales as before");
    }

    @Test
    @DisplayName("what has no kind, divides by zero, or involves a percentage does not fold")
    void refusals() {
        assertNull(fold("calc(2 / 1px)"), "a number over a length");
        assertNull(fold("calc(1px / 1s)"), "a length over a time");
        assertNull(fold("calc(2px * 3px)"), "a length times a length");
        assertNull(fold("calc(10px / 0px)"), "by zero");
        assertNull(fold("calc(50% / 10px)"), "a percentage over pixels");
        assertNull(fold("calc(10px / 50%)"), "pixels over a percentage");
        assertEquals(Quantity.percentage(25), fold("calc(50% / 2)"), "a percentage still scales by numbers");
    }

    @Test
    @DisplayName("scale takes the ratio, and a bare length is still refused")
    void scaleProperty() {
        var style = ComputedStyle.applies("transform", tokens("scale(min(100vw / 2560px, 100vh / 1440px))"), WINDOW);
        assertTrue(style, "the transform applies");
        assertFalse(ComputedStyle.applies("transform", tokens("scale(calc(1px))"), WINDOW));
        var parsed = Transform.parse(tokens("scale(calc(100vw / 3200px))"), Transform.Origin.CENTER, WINDOW);
        assertNotNull(parsed);
        var function = (Transform.Function.Scale) parsed.functions().getFirst();
        assertEquals(0.5, function.x(), 1e-12);
    }
}
