package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.css.Border;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;

/// The containing-block rule on its own: an absolute child's inset is measured
/// from the padding box, which starts inside the border, so the border is
/// added before Yoga sees it, and the padding is not.
///
/// No Yoga node, no frame and no widget, because the arithmetic is the part that
/// can be wrong in a way no picture would show: an inset shifted on the wrong
/// edge draws a caret in the wrong place and draws it perfectly.
/// `AbsolutePlacementTest` is the other half, against the compiled library.
class ContainingBlockTest {

    private static final int RED = 0xFFFF0000;

    private static final Border BORDER = Border.all(2, RED);

    private static final Insets PADDING = Insets.all(Length.points(12));

    private static Length px(float value) {
        return Length.points(value);
    }

    /// `left`/`top` only, which is what most widgets in the toolkit write.
    private static Insets leftTop(float left, float top) {
        return new Insets(px(top), Length.UNDEFINED, Length.UNDEFINED, px(left));
    }

    /// A border whose four sides differ, so an edge shifted by the wrong side
    /// shows.
    private static Border uneven() {
        return Border.all(0, RED).widths(1, 2, 3, 4);
    }

    @Nested
    @DisplayName("what shifts")
    class Shifts {

        @Test
        @DisplayName("an absolute child moves by the containing block's border, per edge")
        void everyDeclaredEdge() {
            var shifted = ContainingBlock.insetFor(Position.ABSOLUTE, new Insets(px(1), px(2), px(3), px(4)), uneven());

            assertEquals(new Insets(px(2), px(4), px(6), px(8)), shifted);
        }

        /// The entry that found the old rule wrong: `top: 0; right: 0` inside a
        /// 2px border lands on the border's inner edge, not on the content.
        @Test
        @DisplayName("left: 0 in a bordered block becomes left: border")
        void zeroIsInsideTheBorder() {
            assertEquals(leftTop(2, 2), ContainingBlock.insetFor(Position.ABSOLUTE, leftTop(0, 0), BORDER));
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
                    new Insets(Length.UNDEFINED, px(2), Length.UNDEFINED, px(2)),
                    ContainingBlock.insetFor(Position.ABSOLUTE, stretched, BORDER));
        }

        @Test
        @DisplayName("a negative inset shifts too, and may end up negative still")
        void negativeInset() {
            assertEquals(leftTop(-18, 4), ContainingBlock.insetFor(Position.ABSOLUTE, leftTop(-20, 2), BORDER));
        }
    }

    @Nested
    @DisplayName("what does not")
    class Holds {

        /// CSS's padding box is bounded by the outer edge of the padding, so
        /// padding alone moves no absolute child.
        @Test
        @DisplayName("a padded block with no border shifts nothing")
        void paddingIsNotAShift() {
            var inset = leftTop(0, 0);

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, Border.NONE));
        }

        /// Flow already placed a relative or static box inside the padding;
        /// shifting it again would move it twice.
        @ParameterizedTest
        @EnumSource(
                value = Position.class,
                names = {"RELATIVE", "STATIC"})
        @DisplayName("a box flow already placed keeps its inset")
        void flowPlacedKeepsItsInset(Position position) {
            var inset = leftTop(4, 4);

            assertSame(inset, ContainingBlock.insetFor(position, inset, BORDER));
        }

        /// The edge Yoga already gets right. Defining it in order to correct it
        /// would replace the static position with a placement the box never
        /// asked for.
        @Test
        @DisplayName("an undefined edge stays undefined")
        void undefinedEdge() {
            var inset = new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, px(0));

            assertEquals(
                    new Insets(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, px(2)),
                    ContainingBlock.insetFor(Position.ABSOLUTE, inset, BORDER));
        }

        /// A percentage resolves against a size the layout pass has not produced
        /// yet, so there is nothing to add it to.
        @Test
        @DisplayName("a percentage inset is left where Yoga puts it")
        void percentInset() {
            var inset = new Insets(Length.percent(50), Length.UNDEFINED, Length.UNDEFINED, Length.percent(50));

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, BORDER));
        }
    }

    @Nested
    @DisplayName("parts written in another box's coordinates")
    class OtherBoxes {

        /// The inverse, which is what makes a tab's underline span the whole
        /// tab: written through it and shifted back, the inset is what was
        /// asked for.
        @Test
        @DisplayName("across the border box, the shift cancels")
        void acrossBorderBoxCancels() {
            var wanted = new Insets(Length.UNDEFINED, px(0), px(0), px(0));
            var written = ContainingBlock.acrossBorderBox(wanted, uneven());

            assertEquals(new Insets(Length.UNDEFINED, px(-2), px(-3), px(-4)), written);
            assertEquals(wanted, ContainingBlock.insetFor(Position.ABSOLUTE, written, uneven()));
        }

        /// A text field's caret at the start of its text: the padding is where
        /// that is, and the border is already counted by the rule.
        @Test
        @DisplayName("in the content box, the padding is added and the border is not counted twice")
        void inContentBoxLandsOnThePadding() {
            var written = ContainingBlock.inContentBox(leftTop(5, 0), PADDING, BORDER);

            assertEquals(leftTop(15, 10), written);
            assertEquals(leftTop(17, 12), ContainingBlock.insetFor(Position.ABSOLUTE, written, BORDER));
        }

        /// A percentage padding cannot be resolved before layout; the text it
        /// pads is measured as though it were zero, and so is this.
        @Test
        @DisplayName("a percentage padding reads as zero")
        void percentPaddingIsZero() {
            var written = ContainingBlock.inContentBox(leftTop(5, 0), Insets.all(Length.percent(10)), Border.NONE);

            assertEquals(leftTop(5, 0), written);
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

            assertSame(inset, ContainingBlock.insetFor(Position.ABSOLUTE, inset, Border.NONE));
            assertSame(inset, ContainingBlock.acrossBorderBox(inset, Border.NONE));
            assertSame(inset, ContainingBlock.inContentBox(inset, Insets.ZERO, Border.NONE));
        }
    }
}
