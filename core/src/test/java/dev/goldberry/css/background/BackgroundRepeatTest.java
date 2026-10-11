package dev.goldberry.css.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.background.BackgroundRepeat.Tiling;

class BackgroundRepeatTest {

    @Test
    @DisplayName("two booleans name the four plain values")
    void of() {
        assertEquals(BackgroundRepeat.REPEAT, BackgroundRepeat.of(true, true));
        assertEquals(BackgroundRepeat.REPEAT_X, BackgroundRepeat.of(true, false));
        assertEquals(BackgroundRepeat.REPEAT_Y, BackgroundRepeat.of(false, true));
        assertEquals(BackgroundRepeat.NO_REPEAT, BackgroundRepeat.of(false, false));
    }

    @Test
    @DisplayName("an axis is drawn more than once unless it is no-repeat, space and round included")
    void acrossAndDown() {
        assertTrue(BackgroundRepeat.REPEAT_X.across());
        assertFalse(BackgroundRepeat.REPEAT_X.down());
        var studs = new BackgroundRepeat(Tiling.SPACE, Tiling.ROUND);
        assertTrue(studs.across());
        assertTrue(studs.down());
        assertFalse(BackgroundRepeat.NO_REPEAT.across());
    }
}
