package io.github.digitalsmile.goldberry.text.flow;

/// Where a line sits in a box wider than it is — CSS's `text-align`.
///
/// The other half of the paragraph-against-its-box question. [WhiteSpace] and
/// [TextOverflow] say what happens when a line is **too wide** for the box;
/// this says what happens when it is too narrow, which until now was always the
/// same thing: it sat at the left edge. §8 has listed `text-align` from the
/// start and `docs/ARCHITECTURE.md` §8 listed it among the properties `Box`
/// cannot express — which was true of `Box` and never true of
/// [io.github.digitalsmile.goldberry.text.Paragraph], where a line already knows
/// its own width and the paint already knows the box's (ADR-0256).
///
/// ## `left` and `right` are refused, and it is not an omission
///
/// ADR-0247 settled the same question for `align-items`: `start` and `end` are
/// Box Alignment's own words and mean "whichever edge text begins at", while
/// `left` and `right` name sides of the
/// screen. The two coincide under LTR and part company under RTL, so accepting
/// `right` as a synonym for [#END] would be writing down an answer that is right
/// today and silently wrong the day bidi run splitting lands. A stylesheet that
/// writes one gets the usual dropped-value warning.
///
/// `justify` is absent for a different reason: it is not a placement but a
/// respacing, and a paragraph here is shaped once and sliced into lines — there
/// is nowhere to put the extra advance without re-shaping.
public enum TextAlign {

    /// The edge text begins at — the left, until there is RTL layout. CSS's
    /// initial value, and what every line in the toolkit did before this existed.
    START,

    /// Centred in whatever room the box has.
    CENTER,

    /// The edge text ends at, which is what a column of numbers beside a row of
    /// faders wants.
    END;

    /// How far into `slack` a line starts — 0, a half, or all of it.
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
    /// **Per line**, which is what `text-align` means: a centred paragraph
    /// centres each of its lines in the same box rather than centring the block
    /// they make up.
    ///
    /// ## Why the rule is here rather than in the paint
    ///
    /// It was in
    /// [io.github.digitalsmile.goldberry.text.Paragraph#paint(io.github.digitalsmile.goldberry.paint.Frame,
    /// double, double, double, int, TextFlow)], privately, and the caret could not
    /// see it — so
    /// [io.github.digitalsmile.goldberry.text.edit.TextGeometry] measured every x
    /// from the paragraph's origin and the painter drew each line indented, and
    /// the two parted company by half a line's slack the moment the text was not
    /// left-aligned. An application hit it first and wrote the rule out a second
    /// time, which is `docs/gaps.md` G30 and exactly the duplication that entry
    /// exists to stop (ADR-0318).
    ///
    /// One implementation, on the property it belongs to: the painter, the caret,
    /// the hit test and the selection all ask *this*, so a fourth reader — a
    /// justified alignment, an RTL line — cannot disagree with the other three.
    ///
    /// Clamped at zero, and both reasons are real. A line **wider** than its box
    /// — every `nowrap` line that overflows — would otherwise be pulled *left* by
    /// [#END], hiding its beginning instead of its end; and `available` is
    /// [io.github.digitalsmile.goldberry.text.Paragraph#UNCONSTRAINED] wherever a
    /// caller is measuring rather than placing, which would make the offset
    /// infinite.
    ///
    /// @param lineWidth what the line measured
    /// @param available the width it was laid out in — what layout gave the box
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
