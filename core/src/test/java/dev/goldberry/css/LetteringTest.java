package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.flow.TextTransform;

class LetteringTest {

    @Test
    @DisplayName("none adds nothing to the face, and any one property makes it something")
    void isNone() {
        assertTrue(Lettering.NONE.isNone());
        assertFalse(Lettering.NONE.letterSpacing(1).isNone());
        assertFalse(Lettering.NONE.transform(TextTransform.UPPERCASE).isNone());
        assertTrue(Lettering.NONE.letterSpacing(1).letterSpacing(0).isNone());
    }
}
