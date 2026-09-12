package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The rule under text an input method has not finished with —
/// `text-composition`. See [Caret] for why this package exists.
///
/// Every platform marks a composition this way and has since the nineties: the
/// characters are there, they are readable, and the line says they are a proposal
/// rather than the document. Drawn **in front of** the glyphs, unlike
/// [Highlight], because it is a mark on them rather than a wash behind them.
///
/// **One per visual line**, like the highlight and for the same reason: a
/// composition in a `text-area` can wrap, and a run of wrapped text is not a
/// rectangle.
///
/// A hairline at every size, which is why its height is the widget's rather than
/// the stylesheet's — an underline that grew with the font would read as a
/// highlight by the time the font was large enough to want one (ADR-0292).
///
/// @param visible whether this rectangle covers any of the composition
public record Underline(boolean visible) implements Widget.Leaf, Styled, Paints {

    /// How thick the rule is, in logical units.
    ///
    /// Not a token, on [Caret]'s width's reasoning inverted: a caret's width is
    /// a matter of taste and has `--gb-caret-width`, where an underline is a
    /// hairline on every platform that draws one, at every size.
    public static final double THICKNESS = 1;

    @Override
    public String cssType() {
        return "text-composition";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // An invisible rule is a box with no background, which is what [Caret]
        // does and for the same reason: zero opacity is in the transition
        // whitelist and would fade.
        return visible ? Box.of().style(style) : Box.of();
    }
}
