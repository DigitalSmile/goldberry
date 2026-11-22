package dev.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// How a misspelled word looks: `text-spelling-error`, which is
/// `::spelling-error`. See [Caret] for why this package exists.
///
/// A carrier, as [Value#carrier] is: it draws nothing. The field draws its
/// words and reads from this box what the cascade resolved for a misspelled
/// one, which is the colour, the decoration lines and how they are drawn, and
/// a background. A field has one only while it checks its spelling, so a
/// field that does not check has the parts it always had.
public record SpellingError() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-spelling-error";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// `::spelling-error`.
    @Override
    public PseudoElement pseudoElement() {
        return PseudoElement.SPELLING_ERROR;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, ""), style.color(), style.textFlow())
                .style(style);
    }
}
