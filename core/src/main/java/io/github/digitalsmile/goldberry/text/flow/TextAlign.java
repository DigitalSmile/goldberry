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
}
