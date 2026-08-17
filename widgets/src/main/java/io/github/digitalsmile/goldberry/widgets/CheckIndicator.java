package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The 16px square with the tick in it — a **part** of [Checkbox], not a widget
/// of its own.
///
/// ## Why a part exists at all
///
/// A checkbox has two surfaces that a theme must be able to style differently:
/// the control, which is 32 tall and holds the label, and the glyph, which is 16
/// square and is what actually turns blue when ticked (`docs/design-system.md`
/// §3). A [ComputedStyle] carries one background, one radius and one border, so
/// one cascade node cannot describe both. Either the glyph's appearance is
/// hard-coded in Java — and then no stylesheet can touch it, which contradicts
/// §11's "colours and metrics only via tokens" — or the glyph is a node the
/// cascade can reach. It is the second.
///
/// ## Parts are CSS-selectable and not KDL-constructible
///
/// `check-indicator` is a CSS type selector and is **not** registered in the KDL
/// inflater, which is a deliberate exception to the parity invariant rather than
/// an oversight in it. The invariant (§11) is about the widgets in the *catalog*:
/// every one of those is constructible three ways because an author picks it from
/// a list and puts it in a document. A part is not in the catalog and has no
/// independent existence — a `check-indicator` outside a `checkbox` is a square
/// that means nothing, and registering the node would let a document create
/// exactly that. What an author wants from a part is to *restyle* it, and a type
/// selector is the whole of that.
///
/// [Checkbox#controlTypes] therefore lists `checkbox` and not this, so the parity
/// test is not asked to build a node that has no business existing.
///
/// @param state      which of the three states to draw, which is also what
///                   `:checked` and `:indeterminate` are mirrored from
/// @param disabled   inherited from the checkbox, so `check-indicator:disabled`
///                   is selectable without a descendant combinator
/// @param thickness  the mark's stroke width in logical pixels
record CheckIndicator(Checkbox.Value state, boolean disabled, double thickness)
        implements Widget.Leaf, Styled, Paints {

    CheckIndicator {
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

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        // Unchecked draws no mark at all rather than a transparent one: an empty
        // box is one fewer path for Blend2D to stroke, and a mark whose colour
        // happened to resolve to something visible would be a tick nobody asked
        // for.
        return switch (state) {
            case UNCHECKED -> box;
            // `style.color()` rather than a colour of its own: the mark is the
            // foreground of this node, exactly as text is the foreground of a
            // `text` node, so `check-indicator:checked { color: … }` is the one
            // rule that moves it.
            case CHECKED -> box.mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), thickness));
            case MIXED -> box.mark(new Box.Mark(Box.Mark.Kind.DASH, style.color(), thickness));
        };
    }
}
