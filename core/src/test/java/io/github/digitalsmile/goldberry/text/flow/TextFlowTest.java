package io.github.digitalsmile.goldberry.text.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The value the cascade hands to the paragraph, and the one rule it enforces.
///
/// The rule is [TextFlow#ellipsises()]: `text-overflow` means nothing without
/// `white-space: nowrap`, and it is decided **here** rather than in the painter so
/// that the measure function and the paint cannot come to different conclusions
/// about the same style ([ADR-0255]).
class TextFlowTest {

    @Test
    @DisplayName("the initial flow is CSS's initial values for all three properties")
    void initialMatchesCss() {
        assertEquals(WhiteSpace.NORMAL, TextFlow.NORMAL.whiteSpace());
        assertEquals(TextOverflow.CLIP, TextFlow.NORMAL.textOverflow());
        assertEquals(TextAlign.START, TextFlow.NORMAL.textAlign());
        assertTrue(TextFlow.NORMAL.wraps());
        assertFalse(TextFlow.NORMAL.ellipsises());
    }

    @Test
    @DisplayName("the two-argument form says nothing about alignment")
    void twoArgumentFormIsStart() {
        // Every caller written before `text-align` was in the subset, which must
        // keep drawing exactly what it drew.
        assertEquals(TextAlign.START, new TextFlow(WhiteSpace.NOWRAP, TextOverflow.ELLIPSIS).textAlign());
        assertEquals(TextFlow.ELLIPSIS, new TextFlow(WhiteSpace.NOWRAP, TextOverflow.ELLIPSIS));
    }

    @Test
    @DisplayName("the slack fraction is 0, a half, and all of it")
    void slackFractions() {
        assertEquals(0.0, TextAlign.START.fractionOfSlack());
        assertEquals(0.5, TextAlign.CENTER.fractionOfSlack());
        assertEquals(1.0, TextAlign.END.fractionOfSlack());
    }

    @Test
    @DisplayName("an ellipsis without nowrap marks nothing")
    void ellipsisNeedsNowrap() {
        // The whole reason this is a method and not a field read. A wrapped line
        // is never too long for its box, so there is no place to put a mark --
        // and a painter that trusted `textOverflow` alone would truncate the last
        // line of a paragraph that had wrapped perfectly well.
        var wrapping = new TextFlow(WhiteSpace.NORMAL, TextOverflow.ELLIPSIS);

        assertTrue(wrapping.textOverflow().marks(), "the property is still what the stylesheet said");
        assertFalse(wrapping.ellipsises(), "and it still has nothing to mark");
    }

    @Test
    @DisplayName("nowrap with ellipsis is the combination that cuts")
    void nowrapWithEllipsisCuts() {
        assertFalse(TextFlow.ELLIPSIS.wraps());
        assertTrue(TextFlow.ELLIPSIS.ellipsises());
        assertEquals(TextFlow.ELLIPSIS, new TextFlow(WhiteSpace.NOWRAP, TextOverflow.ELLIPSIS));
    }

    @Test
    @DisplayName("nowrap without an ellipsis overflows and marks nothing")
    void nowrapAloneOverflows() {
        var clipped = new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP);

        assertFalse(clipped.wraps());
        assertFalse(clipped.ellipsises());
    }

    @Test
    @DisplayName("a wither changes one half and keeps the other")
    void withersAreIndependent() {
        assertEquals(
                TextFlow.ELLIPSIS, TextFlow.NORMAL.whiteSpace(WhiteSpace.NOWRAP).textOverflow(TextOverflow.ELLIPSIS));
        assertEquals(
                WhiteSpace.NORMAL,
                TextFlow.NORMAL.textOverflow(TextOverflow.ELLIPSIS).whiteSpace());
    }

    @Test
    @DisplayName("neither half may be null")
    void neitherHalfMayBeNull() {
        // A flow with a missing half would be a paragraph whose measure function
        // and painter each guessed, which is the failure this record exists to
        // make impossible.
        assertThrows(NullPointerException.class, () -> new TextFlow(null, TextOverflow.CLIP));
        assertThrows(NullPointerException.class, () -> new TextFlow(WhiteSpace.NORMAL, null));
        assertThrows(NullPointerException.class, () -> new TextFlow(WhiteSpace.NORMAL, TextOverflow.CLIP, null));
    }

    @Test
    @DisplayName("the mark is one glyph, not three full stops")
    void theMarkIsOneCharacter() {
        // Three periods measure wider than the ellipsis a font draws for them,
        // so a label truncated with `...` would leave a gap one more letter
        // would have fitted into.
        assertEquals(1, TextOverflow.MARK.codePointCount(0, TextOverflow.MARK.length()));
        assertEquals(0x2026, TextOverflow.MARK.codePointAt(0));
    }

    @Test
    @DisplayName("toString says all three, because a dropped one is invisible otherwise")
    void toStringSaysAllThree() {
        assertEquals("TextFlow[nowrap, ellipsis, start]", TextFlow.ELLIPSIS.toString());
        assertEquals("TextFlow[normal, clip, start]", TextFlow.NORMAL.toString());
        assertEquals(
                "TextFlow[normal, clip, end]",
                TextFlow.NORMAL.textAlign(TextAlign.END).toString());
    }
}
