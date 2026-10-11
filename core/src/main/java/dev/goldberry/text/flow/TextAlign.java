package dev.goldberry.text.flow;

import dev.goldberry.layout.Direction;

/// Where a line sits in a box wider than it is: CSS's `text-align`.
///
/// ```css
/// .readout { text-align: end }
/// ```
///
/// [WhiteSpace] and [TextOverflow] say what happens when a line is too wide for
/// its box; this says what happens when it is too narrow. The placement is made
/// per line, in the paint, because a line already knows its own width there and
/// the paint knows the box's.
///
/// `start` and `end` mean "whichever edge text begins at" and "whichever edge it
/// ends at". [#LEFT] and [#RIGHT] name sides of the box. The two pairs are
/// separate constants rather than aliases. They place a line the same way in a
/// left-to-right box, and part company in a right-to-left one
/// (`direction: rtl`): a `start` line moves to the right edge, and a `left`
/// line stays where it is. [#in] makes that turn.
///
/// [#JUSTIFY] respaces a line rather than placing it: the paragraph is shaped
/// once and sliced into lines, and a justified line is drawn word by word with
/// the slack shared among its gaps. The last line of a paragraph, and one that
/// ends at a newline, is not respaced and sits at the left.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public enum TextAlign {

    /// The edge text begins at: the left, or the right in a right-to-left box.
    /// CSS's initial value.
    START,

    /// Centred in whatever room the box has.
    CENTER,

    /// The edge text ends at, which is what a column of numbers beside a row of
    /// faders wants.
    END,

    /// The box's left edge, whichever way the text runs.
    LEFT,

    /// The box's right edge, whichever way the text runs: the keyword a
    /// stylesheet written for the web reaches for.
    RIGHT,

    /// Both edges: the room left on a line goes into its word gaps. A line
    /// that is not respaced, the last of its paragraph or one without a gap,
    /// sits at the left.
    JUSTIFY;

    /// This alignment as a line is placed in a box running `direction`: in a
    /// right-to-left box, [#START] is the right side and [#END] the left; every
    /// other value, and every value in a left-to-right box, is itself.
    ///
    /// The keyword is what inherits, and this is asked per box, so a
    /// left-to-right child of a right-to-left parent starts at the left.
    public TextAlign in(Direction direction) {
        if (direction == Direction.LTR) {
            return this;
        }
        return switch (this) {
            case START -> RIGHT;
            case END -> LEFT;
            case CENTER, LEFT, RIGHT, JUSTIFY -> this;
        };
    }

    /// The side a line is placed at in a paragraph running `direction`, when
    /// it is placed rather than respaced: [#in(Direction)], with [#JUSTIFY]
    /// read as [#START], since a justified line that is not respaced, the last
    /// of its paragraph, starts at the start.
    public TextAlign placing(Direction direction) {
        return (this == JUSTIFY ? START : this).in(direction);
    }

    /// How far into the slack a line starts: 0, a half, or all of it.
    ///
    /// A fraction rather than a distance, so the one place that knows the room
    /// available is the one place that measures it.
    public double fractionOfSlack() {
        return switch (this) {
            case START, LEFT, JUSTIFY -> 0;
            case CENTER -> 0.5;
            case END, RIGHT -> 1;
        };
    }

    /// How far in from the box's leading edge a line `lineWidth` wide starts, in
    /// a box `available` wide.
    ///
    /// Per line, which is what `text-align` means: a centred paragraph centres
    /// each of its lines in the same box rather than centring the block they
    /// make up. The painter, the caret, the hit test and the selection all ask
    /// this one method, so none of them can disagree with the others about where
    /// a line begins.
    ///
    /// Clamped at zero. A line wider than its box, which is every `nowrap` line
    /// that overflows, would otherwise be pulled left by [#END], hiding its
    /// beginning instead of its end; and `available` is infinite wherever a
    /// caller is measuring rather than placing, which would make the offset
    /// infinite too.
    ///
    /// @param lineWidth what the line measured
    /// @param available the width it was laid out in, which is what layout gave the box
    /// @return a non-negative distance in the same units, and exactly `0` for
    ///         [#START] and [#LEFT]
    public double indentOf(double lineWidth, double available) {
        var fraction = fractionOfSlack();
        if (fraction == 0 || !Double.isFinite(available)) {
            return 0;
        }
        return Math.max(0, available - lineWidth) * fraction;
    }
}
