package dev.goldberry.text.hyphen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class HyphenatorTest {

    @Test
    void matchesPatternsAndPreservesTheMinimumTextOnEitherSide() {
        assertArrayEquals(new int[] {2, 6}, Hyphenator.breaks("hyphenation", Locale.US));
        assertArrayEquals(new int[] {2, 4}, Hyphenator.breaks("automatic", Locale.ENGLISH));
        assertArrayEquals(new int[0], Hyphenator.breaks("area", Locale.US));
    }

    @Test
    void exceptionsOverridePatternsIncludingProhibitedBreaks() {
        assertArrayEquals(new int[] {2, 4}, Hyphenator.breaks("associate", Locale.US));
        assertArrayEquals(new int[0], Hyphenator.breaks("present projects", Locale.US));
    }

    @Test
    void offsetsCountUtf16AcrossPunctuationAndFoldCaseOnlyForMatching() {
        assertArrayEquals(new int[] {14, 18}, Hyphenator.breaks("😃 project! HYPHENATION.", Locale.UK));
        assertArrayEquals(new int[] {3, 7}, Hyphenator.breaks("‘hyphenation’", Locale.forLanguageTag("en-Latn")));
    }

    @Test
    void unsupportedLanguagesAndScriptsKeepTheirWordsWhole() {
        for (var locale : new Locale[] {Locale.ROOT, Locale.FRENCH, Locale.forLanguageTag("en-Cyrl")}) {
            assertArrayEquals(new int[0], Hyphenator.breaks("internationalization", locale));
        }
        assertArrayEquals(new int[0], Hyphenator.breaks("hyphénation cafe\u0301nation hyphenation123", Locale.US));
    }

    @Test
    void aSoftHyphenSuppliesTheWholeWordsOnlyBreaks() {
        assertArrayEquals(new int[0], Hyphenator.breaks("inter\u00adnationalization", Locale.US));
    }

    @Test
    void longWordsAndLongParagraphsHaveSortedBreaksToTheEnd() {
        var word = "hyphenation".repeat(1_000);
        var breaks = Hyphenator.breaks(word, Locale.US);
        assertTrue(breaks.length > 1_000);
        assertEquals(word.length() - 5, breaks[breaks.length - 1]);
        for (var i = 1; i < breaks.length; i++) {
            assertTrue(breaks[i] > breaks[i - 1]);
        }
        var paragraph = "Hyphenation. ".repeat(1_000);
        breaks = Hyphenator.breaks(paragraph, Locale.US);
        assertEquals(2_000, breaks.length);
        assertEquals(paragraph.length() - 7, breaks[breaks.length - 1]);
    }
}
