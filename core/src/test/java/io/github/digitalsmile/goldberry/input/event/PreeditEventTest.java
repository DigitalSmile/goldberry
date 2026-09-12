package io.github.digitalsmile.goldberry.input.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The event a composition arrives in — `docs/gaps.md` G15.
///
/// Almost all of it is [#caret()], which exists because the platform reports a
/// *selection* and a painter wants a *caret*, and because "the platform reports
/// none" is the common answer rather than the exceptional one (ADR-0289).
class PreeditEventTest {

    private static PreeditEvent preedit(String text, int start, int length) {
        return new PreeditEvent(text, start, length, null);
    }

    @Test
    @DisplayName("with no clause reported, the caret is at the end of what has been composed")
    void caretDefaultsToTheEnd() {
        assertEquals(4, preedit("にほんご", -1, -1).caret());
        assertEquals(0, preedit("", -1, -1).caret());
    }

    @Test
    @DisplayName("with a clause, the caret is at its end — which is where an input method puts it")
    void caretFollowsTheClause() {
        assertEquals(2, preedit("にほんご", 0, 2).caret());
        assertEquals(3, preedit("にほんご", 3, 0).caret());
    }

    @Test
    @DisplayName("never outside the string, so a painter need not check")
    void caretIsClamped() {
        assertEquals(4, preedit("にほんご", 3, 99).caret());
        assertEquals(4, preedit("にほんご", 9, 9).caret());
        assertEquals(2, preedit("にほんご", 2, -5).caret(), "a negative length is not a caret before the clause");
    }

    @Test
    @DisplayName("an empty composition is the end of one")
    void emptyIsTheEnd() {
        assertTrue(preedit("", -1, -1).isEnd());
        assertFalse(preedit("に", -1, -1).isEnd());
    }

    @Test
    @DisplayName("carries the platform's own offsets through, unrounded")
    void carriesTheOffsets() {
        var event = preedit("にほんご", 1, 2);

        assertEquals("にほんご", event.text());
        assertEquals(1, event.start());
        assertEquals(2, event.length());
    }

    @Test
    @DisplayName("consumes like every other input event")
    void consumes() {
        var event = preedit("に", -1, -1);

        assertFalse(event.isConsumed());
        event.consume();
        assertTrue(event.isConsumed());
    }
}
