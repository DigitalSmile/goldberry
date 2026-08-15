package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeasuredSizeTest {

    @Test
    @DisplayName("an ordinary measurement is kept as given")
    void ordinaryValues() {
        var size = new MeasuredSize(120.5f, 17f);

        assertEquals(120.5f, size.width());
        assertEquals(17f, size.height());
    }

    @Test
    @DisplayName("zero is a legitimate measurement")
    void zeroIsAllowed() {
        var size = new MeasuredSize(0f, 0f);

        assertEquals(0f, size.width());
        assertEquals(0f, size.height());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, -0.001f})
    @DisplayName("a width Yoga cannot lay out is rejected at the boundary")
    void unusableWidthRejected(float width) {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new MeasuredSize(width, 10f));

        assertTrue(thrown.getMessage().contains("width"), thrown::getMessage);
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f})
    @DisplayName("and so is a height")
    void unusableHeightRejected(float height) {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new MeasuredSize(10f, height));

        assertTrue(thrown.getMessage().contains("height"), thrown::getMessage);
    }

    @Test
    @DisplayName("NaN is named as such, because it is the one that spreads silently")
    void nanIsExplained() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new MeasuredSize(Float.NaN, 1f));

        assertTrue(thrown.getMessage().contains("NaN"), thrown::getMessage);
    }
}
