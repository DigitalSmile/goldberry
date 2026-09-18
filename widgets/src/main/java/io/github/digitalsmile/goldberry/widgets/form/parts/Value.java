package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The text itself — `text-value`. See [Caret] for why this package exists.
///
/// A node of its own rather than text on the field, because a box with text is a
/// measured leaf and Yoga never lays a measured node's children out — so a field
/// that held its own text could hold neither the caret nor the wash behind it.
///
/// It carries whether it is showing the **placeholder**, as a class, because §8's
/// subset has no pseudo-class meaning "standing in for a value" and §3 wants
/// `--gb-text-placeholder` here against `--gb-text` for a real one.
///
/// A `password` hands this the bullets and never the characters — not tidiness:
/// what a widget passes to `Paints.Context.paragraph` is shaped, cached **by its
/// own text**, and drawn, so a part that received a password and chose not to
/// draw it would still have put it in the paragraph cache.
///
/// ## Carrying the ink without the glyphs
///
/// A `text-area` holding a document does **not** hand its text here: it draws
/// one box per visible hard line itself, because a document is shaped a line at
/// a time and only the lines on screen are drawn ([ADR-0388]). It still needs
/// the `text-value` node, because that is where the cascade resolves the
/// colour, the `white-space` and the `.placeholder` rule — so it builds one with
/// [#carrier], which shapes nothing and reports what the cascade said.
///
/// The ink travels back on an **empty paragraph**: [Box.Text] is the only place
/// a box carries a colour for text, so a box with no text can carry no ink.
/// Shaping the empty string costs nothing and is one cache entry for the whole
/// application.
///
/// @param text        what to draw, already masked if the field masks
/// @param placeholder whether `text` is standing in for a value
/// @param carrier     whether the parent draws the glyphs and this node exists
///                    only to answer what the cascade resolved
public record Value(String text, boolean placeholder, boolean carrier) implements Widget.Leaf, Styled, Paints {

    /// A value that draws its own text — every field but `text-area`.
    public Value(String text, boolean placeholder) {
        this(text, placeholder, false);
    }

    /// A `text-value` that shapes nothing and reports the style its parent draws
    /// with. See the note on this class.
    public static Value carrier(boolean placeholder) {
        return new Value("", placeholder, true);
    }

    @Override
    public String cssType() {
        return "text-value";
    }

    @Override
    public Set<String> classes() {
        return placeholder ? Set.of("placeholder") : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (carrier) {
            // An empty paragraph, purely so the box has somewhere to put the
            // colour and the flow. The parent replaces the box; what it keeps
            // is `text().argb()` and `text().flow()`.
            return Box.text(context.paragraph(style, ""), style.color(), style.textFlow())
                    .style(style);
        }
        if (text.isEmpty()) {
            // Not a paragraph of no characters: a measured leaf over an empty
            // string still reports a line's height, so an empty field would stand
            // a line taller than it needs to.
            return Box.of().style(style);
        }
        // The flow and not only the colour, so a field honours the text properties
        // its stylesheet resolved: `text-align` places each line in the box and
        // `text-decoration` marks it. Both are the *cascade's* answer for this node
        // — `text-align` and `text-decoration` inherit, so a rule on the field
        // reaches this label — and the controls place their carets from the same
        // alignment, which is what keeps the caret on the glyphs (`docs/gaps.md`
        // G30, [ADR-0324]).
        //
        // `white-space` and `text-overflow` ride along and mean what they say. No
        // shipped rule sets either on a field: a wrapped `text-area` is the
        // `text-area`'s own business, and it passes a definite width down.
        return Box.text(context.paragraph(style, text), style.color(), style.textFlow())
                .style(style);
    }
}
