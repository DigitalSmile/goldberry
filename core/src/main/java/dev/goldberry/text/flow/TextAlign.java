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
/// `left` and `right` are refused. `start` and `end` mean "whichever edge text
/// begins at" and "whichever edge it ends at", while `left` and `right` name
/// sides of the screen. The two coincide under left-to-right text and part
/// company under right-to-left, so accepting `right` as a synonym for [#END]
/// would be an answer that is right today and silently wrong once bidi line
/// placement lands. A stylesheet that writes one gets the usual dropped-value
/// warning. `justify` is absent because it is a respacing rather than a
/// placement, and a paragraph here is shaped once and sliced into lines, so
/// there is nowhere to put the extra advance without re-shaping.
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
    END;

    /// How far into the slack a line starts: 0, a half, or all of it.
    ///
    /// A fraction rather than a distance, so the one place that knows the room
    /// available is the one place that measures it.
    public double fractionOfSlack() {
        return switch (this) {
            case START -> 0;
            case CENTER -> 0.5;
            case END -> 1;
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
    ///         [#START]
    public double indentOf(double lineWidth, double available) {
        var fraction = fractionOfSlack();
        if (fraction == 0 || !Double.isFinite(available)) {
            return 0;
        }
        return Math.max(0, available - lineWidth) * fraction;
    }
}
