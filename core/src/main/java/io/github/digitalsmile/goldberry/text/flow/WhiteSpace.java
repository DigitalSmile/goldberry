package io.github.digitalsmile.goldberry.text.flow;

/// Whether a paragraph may break a line the author did not — CSS's
/// `white-space`, cut down to the two values a label needs.
///
/// The whole of the difference is what
/// [io.github.digitalsmile.goldberry.text.Paragraph#measureFunction] answers when
/// Yoga offers it a width. [#NORMAL] takes the offer and wraps inside it;
/// [#NOWRAP] reports the width the text actually wants and lets the box overflow.
/// Nothing else in the toolkit reads it.
///
/// ## Why two values and not five
///
/// CSS has `normal`, `nowrap`, `pre`, `pre-wrap` and `pre-line`, and the other
/// three are all statements about **collapsing** — whether runs of spaces and
/// newlines in the source survive into the drawing. Goldberry never collapses
/// anything: a [io.github.digitalsmile.goldberry.text.Paragraph] draws the string
/// it was handed, so `pre-wrap` is what [#NORMAL] already does and `pre` is what
/// [#NOWRAP] already does. Naming them would be four spellings of two behaviours,
/// which is the trap §8's subset has avoided by growing one property at a time
/// against a named need ([ADR-0235], [ADR-0255]).
///
/// ## A hard newline still breaks under `nowrap`
///
/// CSS's `nowrap` collapses `\n` into a space; this does not. A paragraph keeps
/// its explicit lines under either value, and only *soft* wrapping — the
/// `BreakIterator` walk that finds somewhere to break because the line would not
/// fit — is what [#NOWRAP] turns off. The difference is invisible to every
/// consumer in the catalog, all of which are single-line labels, and the
/// alternative is a paragraph whose text is not the string it was given.
public enum WhiteSpace {

    /// Break lines wherever they will not fit, which is what a paragraph of prose
    /// wants and is CSS's initial value.
    NORMAL,

    /// Never break a line that was not already broken, however narrow the box.
    ///
    /// The box then overflows, and what happens to the overhang is
    /// [TextOverflow]'s question: cut off by an `overflow: hidden` ancestor,
    /// ended with an ellipsis, or simply drawn past the edge.
    NOWRAP;

    /// Whether a line too long for its box may be broken.
    public boolean wraps() {
        return this == NORMAL;
    }
}
