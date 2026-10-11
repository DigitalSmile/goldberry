package dev.goldberry.css.compositing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;

/// `filter`, read.
class FilterParserTest {

    private static final int CURRENT = 0xFF112233;

    private static List<Token> value(String css) {
        return CssParser.parseSheet("a { filter: " + css + " }", ParseMode.STRICT, "t")
                .rules()
                .getFirst()
                .declarations()
                .getFirst()
                .value();
    }

    private static List<FilterFunction> parse(String css) {
        return FilterParser.parse(value(css), CssLength.Context.DEFAULT, CURRENT)
                .orElseThrow();
    }

    @Test
    @DisplayName("none is no filter, and a list keeps its order")
    void list() {
        assertEquals(List.of(), parse("none"));
        assertEquals(
                List.of(new FilterFunction.Grayscale(1), new FilterFunction.Opacity(0.6)),
                parse("grayscale(1) opacity(60%)"));
    }

    @Test
    @DisplayName("an amount is a number or a percentage, 1 when omitted, and the unit ones stop at 1")
    void amounts() {
        assertEquals(List.of(new FilterFunction.Brightness(1.5)), parse("brightness(150%)"));
        assertEquals(List.of(new FilterFunction.Saturate(2)), parse("saturate(2)"));
        assertEquals(List.of(new FilterFunction.Sepia(1)), parse("sepia()"));
        assertEquals(List.of(new FilterFunction.Invert(1)), parse("invert(250%)"));
        assertEquals(List.of(new FilterFunction.Contrast(0)), parse("contrast(0)"));
    }

    @Test
    @DisplayName("blur takes a length, hue-rotate an angle")
    void lengthsAndAngles() {
        assertEquals(List.of(new FilterFunction.Blur(4)), parse("blur(4px)"));
        assertEquals(List.of(new FilterFunction.Blur(0)), parse("blur()"));
        assertEquals(List.of(new FilterFunction.HueRotate(90)), parse("hue-rotate(0.25turn)"));
        assertEquals(List.of(new FilterFunction.HueRotate(0)), parse("hue-rotate(0)"));
    }

    @Test
    @DisplayName("drop-shadow takes two or three lengths and a colour before or after, the element's colour by default")
    void dropShadow() {
        assertEquals(
                List.of(new FilterFunction.DropShadow(1, 2, 3, 0xFFFF0000)), parse("drop-shadow(1px 2px 3px red)"));
        assertEquals(List.of(new FilterFunction.DropShadow(1, 2, 0, 0xFF0000FF)), parse("drop-shadow(blue 1px 2px)"));
        assertEquals(List.of(new FilterFunction.DropShadow(1, 2, 0, CURRENT)), parse("drop-shadow(1px 2px)"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "url(#soften)",
                "blur(-1px)",
                "blur(10%)",
                "brightness(-1)",
                "hue-rotate(90px)",
                "drop-shadow(1px)",
                "drop-shadow(red 1px 2px blue)",
                "drop-shadow(1px 2px 3px 4px)",
                "glow(2px)",
                "grayscale(1) none",
            })
    @DisplayName("refuses what is not a filter list it can draw")
    void refused(String css) {
        assertTrue(
                FilterParser.parse(value(css), CssLength.Context.DEFAULT, CURRENT)
                        .isEmpty(),
                css);
    }

    @Test
    @DisplayName("a blur and a shadow reach past the picture, a colour function nowhere")
    void reach() {
        var reach =
                FilterFunction.reach(List.of(new FilterFunction.Blur(2), new FilterFunction.DropShadow(4, 0, 1, 0)));
        assertEquals(6 + 0, reach[0], 1e-9, "left: the blur, and the shadow cast right");
        assertEquals(6 + 7, reach[2], 1e-9, "right: the blur, and the shadow's offset and fade");
        assertEquals(0, FilterFunction.reach(List.of(new FilterFunction.Sepia(1)))[1]);
    }
}
