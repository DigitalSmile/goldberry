package io.github.digitalsmile.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;

/// The rule on its own — ADR-0272.
///
/// No Yoga node, no frame and no widget, because the arithmetic is the part that
/// can be wrong in a way no picture would show: an inset shifted on the wrong
/// edge draws a caret in the wrong place and draws it perfectly.
/// `AbsolutePlacementTest` is the other half, against the compiled library.
class ContainingBlockTest {

    private static final Insets PADDING = Insets.all(Length.points(12));

    private static Length px(float value) {
        return Length.points(value);
    }

    /// `left`/`top` only, which is what every widget in the toolkit writes.
    private static Insets leftTop(float left, float top) {
        return new Insets(px(top), Length.UNDEFINED, Length.UNDEFINED, px(left));
    }

    @Nested
    @DisplayName("what shifts")
    class Shifts {

        @Test
        @DisplayName("an absolute child moves by the containing block's padding, per edge")
        void everyDeclaredEdge() {
            var shifted = ContainingBlock.insetFor(
                    Position.ABSOLUTE,
                    new Insets(px(1), px(2), px(3), px(4)),
                    new Insets(px(10), px(20), px(30), px(40)));

            assertEquals(new Insets(px(11), px(22), px(33), px(44)), shifted);
        }

        /// The one every field, text area and caret depends on: `left: 0` inside
        /// a 12px-padded control has to reach Yoga as `left: 12`, because Yoga
        /// measures it from the border box (ADR-0265).
        @Test
        @DisplayName("left: 0 in a padded block becomes left: padding")
        void zeroIsNotNothing() {
            assertEquals(leftTop(12, 12), ContainingBlock.insetFor(Position.ABSOLUTE, leftTop(0, 0), PADDING));
        }

        /// Both edges of an axis, which is the case a correction applied *after*
        /// the layout pass could not have handled: Yoga derives the child's width
        /// from the two insets, so shifting both is what makes that width the
        /// padding box's rather than the border box's.
        @Test
        @DisplayName("both edges of an axis shift, so the derived size is the padding box's")
        void bothEdgesOfAnAxis() {
            var stretched = new Insets(Length.UNDEFINED, px(0), Length.UNDEFINED, px(0));

            assertEquals(
                    new Insets(Length.UNDEFINED, px(12), Length.UNDEFINED, px(12)),
                    ContainingBlock.insetFor(Position.ABSOLUTE, stretched, PADDING));
        }

        @Test
        @DisplayName("a negative inset shifts too, and may end up negative still")
        void negativeInset() {
            assertEquals(leftTop(-8, 4), ContainingBlock.insetFor(Position.ABSOLUTE, leftTop(-20, -8), PADDING));
        }
    }

    @Nested
    @DisplayName("what does not")
    class Holds {

        /// Flow already placed a relative box inside the padding; shifting it
        /// again would move it by the padding twice.
        @Test
        @DisplayName("a relative box keeps its inset")
        void relative() {
            var inset = leftTop(4, 4);
            assertSame(inset, ContainingBlock.insetFor(Position.RELATIVE, inset, PADDING));
        }

        @Test
        @DisplayName("a static box keeps its inset")
        void staticPosition() {
            var inset = leftTop(4, 4);
            assertSame(inset, ContainingBlock.insetFor(Position.STATIC, inset, PADDING));
        }

        /// The edge Yoga already gets right. Defining it in order to correct it
        /// would replace the static position — which includes the padding — with
        /// a placement the box never asked for.
        @Test
        @DisplayName("an undefined edge stays undefined")
        void undefinedEdge() {
            var inset = new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, px(0));

            assertEquals(
                    new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, px(12)),
                    ContainingBlock.insetFor(Position.ABSOLUTE, inset, PADDING));
        }

        /// A percentage resolves against a size the layout pass has not produced
        /// yet, so there is nothing to add it to. Documented rather than guessed
        /// at — the same restriction `TextField.leftPadding` states.
        @Test
        @DisplayName("a percentage inset is left where Yoga puts it")
        void percentInset() {
            var inset = new Insets(Length.percent(50), Length.UNDEFINED, Length.UNDEFINED, Length.percent(50));

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, PADDING));
        }

        @Test
        @DisplayName("a percentage padding shifts nothing")
        void percentPadding() {
            var inset = leftTop(0, 0);

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, Insets.all(Length.percent(10))));
        }
    }

    @Nested
    @DisplayName("the identity the guard reads")
    class Identity {

        /// Not merely equal. `RenderObject` compares the result against the inset
        /// already on the node to decide whether to call Yoga at all, and a fresh
        /// instance every frame would be an allocation per absolute node per
        /// frame to arrive at the same answer.
        @Test
        @DisplayName("nothing to shift hands the same instance back")
        void sameInstanceWhenNothingShifts() {
            var inset = leftTop(4, 4);

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, Insets.ZERO));
        }

        @Test
        @DisplayName("an undefined padding is Yoga's zero, so it shifts nothing either")
        void undefinedPadding() {
            var inset = leftTop(4, 4);

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, Insets.all(Length.UNDEFINED)));
        }
    }
}
