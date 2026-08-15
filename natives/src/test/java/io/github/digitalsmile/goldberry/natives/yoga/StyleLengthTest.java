package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// The value type that puts Yoga's three setters per property back together.
class StyleLengthTest {

    @Test
    @DisplayName("a length remembers its unit")
    void unitsAreNotInterchangeable() {
        assertEquals(new StyleLength.Points(50f), StyleLength.points(50f));
        assertEquals(new StyleLength.Percent(50f), StyleLength.percent(50f));

        // 50px and 50% resolve to different numbers against any parent but one,
        // so they had better not compare equal.
        assertNotEquals(StyleLength.points(50f), StyleLength.percent(50f));
    }

    @Test
    @DisplayName("the keywords are singletons")
    void keywordsAreSingletons() {
        assertSame(StyleLength.Keyword.AUTO, StyleLength.AUTO);
        assertSame(StyleLength.Keyword.UNDEFINED, StyleLength.UNDEFINED);
        assertNotEquals(StyleLength.AUTO, StyleLength.UNDEFINED);
    }

    @Test
    @DisplayName("NaN is refused, because it is how Yoga spells undefined")
    void nanIsRefused() {
        // Admitting NaN would give one state two spellings, and the second of
        // them does not even equal itself.
        var thrown = assertThrows(
                IllegalArgumentException.class, () -> StyleLength.points(Float.NaN));

        assertTrue(thrown.getMessage().contains("UNDEFINED"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> StyleLength.percent(Float.NaN));
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    @DisplayName("an infinite length is refused")
    void infinityIsRefused(float value) {
        assertThrows(IllegalArgumentException.class, () -> StyleLength.points(value));
        assertThrows(IllegalArgumentException.class, () -> StyleLength.percent(value));
    }

    @Test
    @DisplayName("a negative length is allowed, because a negative margin is meaningful")
    void negativeLengthsAreAllowed() {
        assertEquals(-8f, ((StyleLength.Points) StyleLength.points(-8f)).value());
    }

    @Test
    @DisplayName("a length prints as a length")
    void lengthsPrintReadably() {
        assertEquals("4.0px", StyleLength.points(4f).toString());
        assertEquals("50.0%", StyleLength.percent(50f).toString());
        assertEquals("auto", StyleLength.AUTO.toString());
        assertEquals("undefined", StyleLength.UNDEFINED.toString());
    }
}
