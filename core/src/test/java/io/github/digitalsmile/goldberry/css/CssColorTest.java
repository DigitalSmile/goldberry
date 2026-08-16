package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CssColorTest {

    /// Parses a colour the way it would arrive: as a declaration's value.
    private static Integer parse(String css) {
        return CssColor.parse(
                CssParser.parse("a { color: " + css + " }").getFirst()
                        .declarations().getFirst().value());
    }

    @ParameterizedTest
    @CsvSource({
            "'#000000', 0xFF000000",
            "'#ffffff', 0xFFFFFFFF",
            "'#2e3440', 0xFF2E3440",
            "'#88c0d0', 0xFF88C0D0",
    })
    @DisplayName("six-digit hex is opaque")
    void sixDigitHex(String css, long expected) {
        assertEquals((int) expected, parse(css));
    }

    @Test
    @DisplayName("three-digit hex doubles each digit rather than shifting")
    void threeDigitHex() {
        // #abc is #aabbcc, not #0a0b0c -- a shift would silently darken every
        // short-form colour in a stylesheet.
        assertEquals(0xFFAABBCC, parse("#abc"));
        assertEquals(0xFFFFFFFF, parse("#fff"));
    }

    @Test
    @DisplayName("eight-digit hex moves the alpha from last to first")
    void eightDigitHex() {
        // CSS writes #rrggbbaa; the packed form is 0xAARRGGBB.
        assertEquals(0x8088C0D0, parse("#88c0d080"));
        assertEquals(0x00FF0000, parse("#ff000000"));
    }

    @Test
    @DisplayName("four-digit hex is the short form with alpha")
    void fourDigitHex() {
        assertEquals(0x88AABBCC, parse("#abc8"));
    }

    @ParameterizedTest
    @CsvSource({
            "'rgb(46, 52, 64)', 0xFF2E3440",
            "'rgb(46 52 64)', 0xFF2E3440",
            "'rgba(46, 52, 64, 1)', 0xFF2E3440",
    })
    @DisplayName("rgb() in the comma form and the space form")
    void rgbForms(String css, long expected) {
        assertEquals((int) expected, parse(css));
    }

    @Test
    @DisplayName("an rgba alpha is a fraction of 255")
    void rgbaAlpha() {
        assertEquals(0x802E3440, parse("rgba(46, 52, 64, 0.5)"));
        assertEquals(0x002E3440, parse("rgba(46, 52, 64, 0)"));
    }

    @Test
    @DisplayName("the slash form carries the alpha too")
    void slashAlpha() {
        assertEquals(0x802E3440, parse("rgb(46 52 64 / 50%)"));
    }

    @Test
    @DisplayName("a percentage channel is a fraction of 255, not of 100")
    void percentageChannels() {
        assertEquals(0xFFFF0000, parse("rgb(100%, 0%, 0%)"));
    }

    @Test
    @DisplayName("out-of-range channels clamp rather than fail")
    void clamping() {
        // The author clearly meant "as much red as there is".
        assertEquals(0xFFFF0000, parse("rgb(300, -20, 0)"));
    }

    @Test
    @DisplayName("named colours and transparent")
    void named() {
        assertEquals(0xFFFF0000, parse("red"));
        assertEquals(0xFF000000, parse("black"));
        assertEquals(0xFF808080, parse("gray"));
        // Both spellings, because getting this wrong looks like a toolkit bug.
        assertEquals(parse("gray"), parse("grey"));
        assertEquals(0x00000000, parse("transparent"));
    }

    @Test
    @DisplayName("a name is case-insensitive")
    void caseInsensitive() {
        assertEquals(parse("red"), parse("RED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "#12",           // no such hex length
            "#12345",
            "#gggggg",       // not hex digits
            "papayawhip",    // outside the Level 1 set, on purpose
            "rgb(1, 2)",     // too few channels
            "rgb(1, 2, 3, 4, 5)",
            "4px",
            "var(--x)",      // unresolved: the resolver deals with these first
    })
    @DisplayName("what is not a colour returns null rather than a guess")
    void notAColour(String css) {
        // Null and not TRANSPARENT: the caller has to be able to tell "no colour
        // here" from "deliberately invisible".
        assertNull(parse(css));
    }
}
