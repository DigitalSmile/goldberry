package dev.goldberry.widgets.controls.checkbox;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The 16px square with the tick in it — a **part** of [Checkbox], styleable
/// as `check-indicator` and not a widget a document can write.
///
/// A checkbox has two surfaces that a theme must be able to style differently:
/// the control, which is 32 tall and holds the label, and the glyph, which is 16
/// square and is what fills with the accent when ticked. A [ComputedStyle]
/// carries one background, one radius and one border, so one cascade node
/// cannot describe both; the glyph is therefore a node the cascade can reach.
///
/// It is **not** registered in the KDL inflater. A `check-indicator` outside a
/// `checkbox` is a square that means nothing, and registering the node would
/// let a document create exactly that. What an author wants from a part is to
/// *restyle* it, and a type selector is the whole of that, so
/// [dev.goldberry.widgets.Controls#controlTypes()] lists `checkbox` and not
/// this. Public only so that a `tree` row can draw the same box.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#checkbox).
///
/// @param state      which of the three states to draw, which is also what
///                   `:checked` and `:indeterminate` are mirrored from
/// @param disabled   inherited from the checkbox, so `check-indicator:disabled`
///                   is selectable without a descendant combinator
/// @param thickness  the mark's stroke width in logical pixels
public record CheckIndicator(Checkbox.Value state, boolean disabled, double thickness)
        implements Widget.Leaf, Styled, Paints {

    public CheckIndicator {
        Objects.requireNonNull(state, "state");
    }

    @Override
    public String cssType() {
        return "check-indicator";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public boolean isChecked() {
        return state == Checkbox.Value.CHECKED;
    }

    @Override
    public boolean isIndeterminate() {
        return state == Checkbox.Value.MIXED;
    }

    /// The mark, always — see [CheckMark] for why it is a node rather than a mark
    /// on this box, and why it is built in every state including `UNCHECKED`.
    ///
    /// The colour still comes from `style.color()` and still **inherits**, so
    /// `check-indicator:checked { color: … }` is the one rule that moves it and
    /// nothing has to name the mark's node to recolour it.
    @Override
    public List<Widget> children() {
        return List.of(new CheckMark(state, disabled, thickness));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
