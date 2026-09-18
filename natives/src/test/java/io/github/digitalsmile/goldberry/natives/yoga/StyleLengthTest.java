package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;

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
    @DisplayName("NaN is refused, because it is how Yoga spells undefined")
    void nanIsRefused() {
        // Admitting NaN would give one state two spellings, and the second of
        // them does not even equal itself.
        var thrown = assertThrows(IllegalArgumentException.class, () -> StyleLength.points(Float.NaN));

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
    @DisplayName("a length names its unit, so 50px and 50% are not one line in a log")
    void lengthsNameTheirUnit() {
        // The claim is that the four forms are distinguishable and say which they
        // are; the exact spelling is not a contract, so it is not pinned here.
        var points = StyleLength.points(50f).toString();
        var percent = StyleLength.percent(50f).toString();

        assertTrue(points.contains("px"), points);
        assertTrue(percent.contains("%"), percent);
        assertEquals(
                4,
                Set.of(points, percent, StyleLength.AUTO.toString(), StyleLength.UNDEFINED.toString())
                        .size(),
                "two lengths that print the same are two lengths a reader cannot tell apart");
    }
}
