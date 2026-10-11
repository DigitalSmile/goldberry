package dev.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Stretch;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The wash behind selected text — `text-selection`. See [Caret] for why this
/// package exists.
///
/// Drawn *behind* the glyphs rather than over them, which is why it is built
/// first: selected text must stay readable, and a translucent wash
/// over a glyph dims the glyph.
///
/// **One per visual line.** A `text-input` has exactly one; a `text-area` has one
/// per line the selection covers, because a run of text that wraps is not a
/// rectangle. That is the only difference between the two controls' selections,
/// and it is why `Paragraph`'s measurements take a *line's* range.
///
/// A line with text in both directions can need more than one: a selection
/// across a right-to-left run is a piece on each side of it. [#over] makes the
/// one box a line has cover several stretches.
///
/// @param visible whether this rectangle covers anything
public record Highlight(boolean visible) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-selection";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// `::selection`.
    @Override
    public PseudoElement pseudoElement() {
        return PseudoElement.SELECTION;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return visible ? Box.of().style(style) : Box.of();
    }

    /// The box to place over the hull of `stretches`, a line's highlight
    /// rendered as `drawn`, `height` tall: `drawn` itself for one stretch, or
    /// an undrawn box holding a copy of it over each, measured from the
    /// hull's left edge.
    ///
    /// The caller places the result as it placed one highlight, at
    /// [#hull]'s left and width.
    public static Box over(Box drawn, List<Stretch> stretches, Length height) {
        if (stretches.size() <= 1) {
            return drawn;
        }
        var left = stretches.getFirst().left();
        var pieces = new Box[stretches.size()];
        for (var i = 0; i < pieces.length; i++) {
            var stretch = stretches.get(i);
            pieces[i] = drawn.position(Position.ABSOLUTE)
                    .inset(new Insets(Length.points(0), Length.UNDEFINED, Length.UNDEFINED, Length.points((float)
                            (stretch.left() - left))))
                    .size(Length.points((float) stretch.width()), height);
        }
        return Box.of().children(pieces);
    }

    /// The stretch from the left of the first of `stretches` to the right of
    /// the last, or `fallback` when there are none.
    public static Stretch hull(List<Stretch> stretches, Stretch fallback) {
        return stretches.isEmpty()
                ? fallback
                : new Stretch(stretches.getFirst().left(), stretches.getLast().right());
    }
}
