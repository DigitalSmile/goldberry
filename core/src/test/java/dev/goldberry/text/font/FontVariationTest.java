package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FontVariationTest {

    @Test
    @DisplayName("normal width and no settings ask a face for nothing beyond its weight")
    void isNone() {
        assertTrue(FontVariation.NONE.isNone());
        assertFalse(FontVariation.NONE.stretch(75).isNone());
        assertFalse(FontVariation.NONE
                .settings(List.of(new FontVariation.Setting("GRAD", 50)))
                .isNone());
    }

    @Test
    @DisplayName("a stretch is 50% to 200%")
    void stretchRange() {
        assertThrows(IllegalArgumentException.class, () -> FontVariation.NONE.stretch(40));
        assertThrows(IllegalArgumentException.class, () -> FontVariation.NONE.stretch(201));
    }
}
