package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What a layout value may be, with no layout engine under it.
///
/// These are the types `paint.Box` and `css.ComputedStyle` are written in, and
/// they were the layout engine's own until ADR-0279 — so what is asserted here is
/// mostly that the mirror is faithful. The half that is not bookkeeping is the
/// NaN rule: the engine spells "undefined" as a NaN, and letting one in through
/// the front door would give one state two spellings.
class LengthTest {

    @Nested
    @DisplayName("what a length may be")
    class Lengths {

        @Test
        @DisplayName("a NaN is refused, because it is how undefined is spelled")
        void nanIsRefused() {
            // Admitting it would make `equals` disagree with itself: NaN != NaN,
            // so two boxes written the same way would be unequal and every
            // style-change guard in the render tree would fire every frame.
            var raised = assertThrows(IllegalArgumentException.class, () -> Length.points(Float.NaN));
            assertTrue(raised.getMessage().contains("UNDEFINED"), () -> "and it says so: " + raised.getMessage());
            assertThrows(IllegalArgumentException.class, () -> Length.percent(Float.NaN));
        }

        @Test
        @DisplayName("an infinite length is refused")
        void infiniteIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> Length.points(Float.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> Length.percent(Float.NEGATIVE_INFINITY));
        }

        @Test
        @DisplayName("a negative length is allowed, because a negative margin is meaningful")
        void negativeIsAllowed() {
            assertEquals(-8f, ((Length.Points) Length.points(-8)).value());
        }

        @Test
        @DisplayName("a length prints as a stylesheet would write it")
        void printsAsCss() {
            assertEquals("8.0px", Length.points(8).toString());
            assertEquals("50.0%", Length.percent(50).toString());
            assertEquals("auto", Length.AUTO.toString());
            assertEquals("undefined", Length.UNDEFINED.toString());
        }
    }

    @Nested
    @DisplayName("insets")
    class FourEdges {

        @Test
        @DisplayName("symmetric is CSS's two-value form")
        void symmetric() {
            // `padding: 0 12px` -- what a button is before it is anything else.
            var padding = Insets.symmetric(Length.points(0), Length.points(12));

            assertEquals(Length.points(0), padding.top());
            assertEquals(Length.points(12), padding.right());
            assertEquals(Length.points(0), padding.bottom());
            assertEquals(Length.points(12), padding.left());
        }

        @Test
        @DisplayName("zero and none are different things")
        void zeroIsNotNone() {
            // An inset of zero pins a node to that edge; an undefined one leaves
            // it where flow put it (ADR-0272).
            assertNotEquals(Insets.ZERO, Insets.NONE);
            assertEquals(Length.points(0), Insets.ZERO.top());
            assertEquals(Length.UNDEFINED, Insets.NONE.top());
        }

        @Test
        @DisplayName("uniform is four of anything")
        void uniform() {
            assertTrue(Insets.all(Length.points(8)).isUniform());
            assertTrue(Insets.NONE.isUniform());
            assertFalse(Insets.symmetric(Length.points(0), Length.points(12)).isUniform());
        }

        @Test
        @DisplayName("a null edge is refused rather than defaulted")
        void nullsRefused() {
            assertThrows(
                    NullPointerException.class,
                    () -> new Insets(null, Length.points(0), Length.points(0), Length.points(0)));
        }
    }

    @Nested
    @DisplayName("limits")
    class FourLimits {

        @Test
        @DisplayName("no limit is undefined on all four, and knows it")
        void none() {
            assertTrue(Limits.NONE.isNone());
            assertEquals(Length.UNDEFINED, Limits.NONE.maxWidth());
        }

        @Test
        @DisplayName("a maximum of zero is a real limit, and not the absence of one")
        void zeroIsALimit() {
            // The reason "no limit" is undefined rather than zero: a minimum of
            // zero constrains nothing, but a maximum of zero is a box that may
            // not exist.
            assertFalse(Limits.NONE.maxWidth(Length.points(0)).isNone());
            assertFalse(Limits.NONE.minWidth(Length.points(0)).isNone());
        }

        @Test
        @DisplayName("each wither changes one of the four and keeps the rest")
        void withers() {
            var limits = Limits.NONE.minWidth(Length.points(320)).maxWidth(Length.percent(80));

            assertEquals(Length.points(320), limits.minWidth());
            assertEquals(Length.percent(80), limits.maxWidth());
            assertEquals(Length.UNDEFINED, limits.minHeight());
            assertEquals(Length.UNDEFINED, limits.maxHeight());
        }
    }
}
