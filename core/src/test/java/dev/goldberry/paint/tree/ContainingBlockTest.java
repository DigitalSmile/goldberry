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
/// from the padding box, which starts inside the border. A border takes room in
/// layout and Yoga measures the inset from there itself, so nothing is added
/// before Yoga sees it; what is left is the arithmetic for parts a widget
/// writes in another box's coordinates.
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
    @DisplayName("what Yoga is given")
    class Given {

        /// Yoga measures an absolute inset from the padding box, inside the
        /// border, which is where CSS measures it: the declared inset is the
        /// one to give it, whatever the border.
        @ParameterizedTest
        @EnumSource(Position.class)
        @DisplayName("the declared inset, itself, whatever the border")
        void declared(Position position) {
            var inset = new Insets(px(1), px(2), px(3), px(4));

            assertSame(inset, ContainingBlock.insetFor(position, inset, uneven()));
            assertSame(inset, ContainingBlock.insetFor(position, inset, Border.NONE));
        }
    }

    @Nested
    @DisplayName("parts written in another box's coordinates")
    class OtherBoxes {

        /// What makes a tab's underline span the whole tab: the padding box's
        /// inset, moved out by the border on each side.
        @Test
        @DisplayName("across the border box, each edge moves out by its border")
        void acrossBorderBox() {
            var wanted = new Insets(Length.UNDEFINED, px(0), px(0), px(0));

            assertEquals(
                    new Insets(Length.UNDEFINED, px(-2), px(-3), px(-4)),
                    ContainingBlock.acrossBorderBox(wanted, uneven()));
        }

        /// The edge Yoga places by flow, and a percentage of a size layout has
        /// not produced, are left alone.
        @Test
        @DisplayName("an undefined edge and a percentage stay as they are")
        void undefinedAndPercent() {
            var inset = new Insets(Length.UNDEFINED, Length.percent(50), Length.UNDEFINED, px(0));

            assertEquals(
                    new Insets(Length.UNDEFINED, Length.percent(50), Length.UNDEFINED, px(-4)),
                    ContainingBlock.acrossBorderBox(inset, uneven()));
        }

        /// A text field's caret at the start of its text: the padding is where
        /// that is, from the padding box Yoga measures from.
        @Test
        @DisplayName("in the content box, the padding is added and the border is not")
        void inContentBoxLandsOnThePadding() {
            assertEquals(leftTop(17, 12), ContainingBlock.inContentBox(leftTop(5, 0), PADDING, BORDER));
        }

        /// A percentage padding cannot be resolved before layout; the text it
        /// pads is measured as though it were zero, and so is this.
        @Test
        @DisplayName("a percentage padding reads as zero")
        void percentPaddingIsZero() {
            var written = ContainingBlock.inContentBox(leftTop(5, 0), Insets.all(Length.percent(10)), BORDER);

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

            assertSame(inset, ContainingBlock.acrossBorderBox(inset, Border.NONE));
            assertSame(inset, ContainingBlock.inContentBox(inset, Insets.ZERO, BORDER));
        }
    }
}
