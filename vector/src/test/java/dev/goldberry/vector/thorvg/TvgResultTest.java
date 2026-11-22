package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TvgResult")
class TvgResultTest {

    @Test
    @DisplayName("names every code thorvg_capi.h lists, and calls the rest UNKNOWN")
    void names() {
        for (var result : TvgResult.values()) {
            assertEquals(result, TvgResult.of(result.code()));
        }
        assertEquals(TvgResult.NOT_SUPPORTED, TvgResult.of(5));
        assertEquals(TvgResult.UNKNOWN, TvgResult.of(42));
        assertEquals("ThorVG answered INVALID_ARGUMENT (1)", TvgResult.describe(1));
    }
}
