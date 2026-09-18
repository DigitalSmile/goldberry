package io.github.digitalsmile.goldberry.text.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("a flow says nothing about decoration unless it is asked to")
    void decorationsAreEmptyByDefault() {
        assertEquals(TextDecoration.NONE, TextFlow.NORMAL.decorations());
        assertEquals(TextDecoration.NONE, TextFlow.ELLIPSIS.decorations());
        assertFalse(TextFlow.NORMAL.isDecorated());
        // Every caller written before `text-decoration` was in the subset, which
        // must keep drawing exactly what it drew.
        assertEquals(TextFlow.NORMAL, new TextFlow(WhiteSpace.NORMAL, TextOverflow.CLIP, TextAlign.START));
    }

    @Test
    @DisplayName("both rules can be asked for at once")
    void decorationsAreASet() {
        var both = TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE, TextDecoration.LINE_THROUGH);

        assertTrue(both.isDecorated());
        assertTrue(both.has(TextDecoration.UNDERLINE));
        assertTrue(both.has(TextDecoration.LINE_THROUGH));
        assertEquals(TextFlow.NORMAL, both.decorations(TextDecoration.NONE), "clearing them gets the plain flow back");
    }

    @Test
    @DisplayName("a flow is a value, so the set it was handed cannot be changed underneath it")
    void theSetIsCopied() {
        var mutable = new java.util.LinkedHashSet<TextDecoration>();
        mutable.add(TextDecoration.UNDERLINE);
        var flow = TextFlow.NORMAL.decorations(mutable);

        mutable.add(TextDecoration.LINE_THROUGH);

        assertEquals(java.util.Set.of(TextDecoration.UNDERLINE), flow.decorations());
    }

    @Test
    @DisplayName("the CSS names are what a stylesheet writes")
    void decorationNames() {
        assertEquals("line-through", TextDecoration.LINE_THROUGH.cssName());
        assertEquals(TextDecoration.UNDERLINE, TextDecoration.parse("underline"));
        assertEquals(TextDecoration.LINE_THROUGH, TextDecoration.parse("LINE-THROUGH"));
        assertNull(TextDecoration.parse("wavy"), "an unknown value is a dropped declaration, not an exception");
    }

    @Test
    @DisplayName("the indent is the fraction of the room actually left over")
    void indentOfTheSlack() {
        // The rule the painter, the caret, the hit test and the selection all
        // share, so that none of them can hold a second copy of it
        // (`docs/gaps.md` G30, ADR-0318).
        assertEquals(0.0, TextAlign.START.indentOf(40, 100), "start never indents");
        assertEquals(30.0, TextAlign.CENTER.indentOf(40, 100));
        assertEquals(60.0, TextAlign.END.indentOf(40, 100));
    }

    @Test
    @DisplayName("a line with no room to move does not move")
    void indentIsClampedAtZero() {
        // A `nowrap` line wider than its box: `end` would otherwise pull it left
        // and hide its beginning instead of its end.
        assertEquals(0.0, TextAlign.END.indentOf(140, 100));
        assertEquals(0.0, TextAlign.CENTER.indentOf(140, 100));
        assertEquals(0.0, TextAlign.CENTER.indentOf(40, 40), "a line that fills its box has no slack");
    }

    @Test
    @DisplayName("an unconstrained box is measured in, not placed in")
    void indentNeedsAFiniteBox() {
        // Every caller that is measuring rather than drawing passes
        // `Paragraph.UNCONSTRAINED`, and half of infinity is not an indent.
        assertEquals(0.0, TextAlign.CENTER.indentOf(40, Double.POSITIVE_INFINITY));
        assertEquals(0.0, TextAlign.END.indentOf(40, Double.NaN));
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
    @DisplayName("toString says all four, because a dropped one is invisible otherwise")
    void toStringSaysAllThree() {
        assertEquals("TextFlow[nowrap, ellipsis, start, none]", TextFlow.ELLIPSIS.toString());
        assertEquals("TextFlow[normal, clip, start, none]", TextFlow.NORMAL.toString());
        assertEquals(
                "TextFlow[normal, clip, end, none]",
                TextFlow.NORMAL.textAlign(TextAlign.END).toString());
        // In the enum's order rather than the set's, so two equal flows print the
        // same however each was built.
        assertEquals(
                "TextFlow[normal, clip, start, underline line-through]",
                TextFlow.NORMAL
                        .decorations(TextDecoration.LINE_THROUGH, TextDecoration.UNDERLINE)
                        .toString());
    }
}
