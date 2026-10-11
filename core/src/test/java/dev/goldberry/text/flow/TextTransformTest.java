package dev.goldberry.text.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// `text-transform`, one character for one.
class TextTransformTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "UPPERCASE  | hello, world      | HELLO, WORLD",
                "LOWERCASE  | Hello, WORLD      | hello, world",
                "CAPITALIZE | hello wide world  | Hello Wide World",
                "CAPITALIZE | rock-n-roll       | Rock-N-Roll",
                "CAPITALIZE | don't stop        | Don't Stop",
                "CAPITALIZE | (aside) 2nd try   | (Aside) 2nd Try",
                "NONE       | Hello             | Hello",
                "UPPERCASE  | straße            | STRAßE",
                "UPPERCASE  | ǆ                 | Ǆ",
                "CAPITALIZE | ǆungla            | ǅungla",
            })
    @DisplayName("changes case and keeps every offset")
    void transforms(TextTransform transform, String text, String expected) {
        var drawn = transform.apply(text);
        assertEquals(expected, drawn);
        assertEquals(text.length(), drawn.length(), "the same length, so a caret maps one to one");
    }

    @Test
    @DisplayName("a character outside the basic plane is cased in place")
    void supplementary() {
        var deseret = "𐐨"; // DESERET SMALL LETTER LONG I
        assertEquals("𐐀", TextTransform.UPPERCASE.apply(deseret));
    }

    @Test
    @DisplayName("reads CSS's four keywords and nothing else")
    void parse() {
        assertEquals(TextTransform.UPPERCASE, TextTransform.parse("UpperCase"));
        assertNull(TextTransform.parse("full-width"));
    }
}
