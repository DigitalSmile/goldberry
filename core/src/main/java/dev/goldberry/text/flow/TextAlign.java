package dev.goldberry.text.flow;

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
/// separate constants rather than aliases. They place a line the same way while
/// every line is set left to right, which is all the toolkit does today, and
/// they part company once a right-to-left line is placed from the right: a
/// `start` line moves to the right edge, and a `left` line stays where it is.
///
/// `justify` is refused, with a warning that says so. It respaces a line rather
/// than placing it, and a paragraph here is shaped once and sliced into lines,
/// so there is nowhere to put the extra advance without shaping it again.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public enum TextAlign {

    /// The edge text begins at: the left, until there is right-to-left layout.
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
    RIGHT;

    /// How far into the slack a line starts: 0, a half, or all of it.
    ///
    /// A fraction rather than a distance, so the one place that knows the room
    /// available is the one place that measures it.
    public double fractionOfSlack() {
        return switch (this) {
            case START, LEFT -> 0;
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
