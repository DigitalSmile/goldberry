package io.github.digitalsmile.goldberry.widgets.core.affix;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

/// The part of an [Affix] that actually moves.
///
/// A translate, for `scroll-content`'s reason: it costs no layout, so a header
/// that stays put while a thousand rows scroll under it re-runs Yoga exactly
/// never. It is also what keeps the hole above it the size it was — a margin or
/// an inset would move the hole too, which is the one thing §1 says must not
/// happen.
record AffixContent(List<Widget> children, Edge edge, double shift)
        implements Widget.Leaf, Styled, Paints {

    AffixContent {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        if (shift == 0) {
            return resolved;
        }
        var length = Transform.Length.px(shift);
        var zero = Transform.Length.ZERO;
        return resolved.transform(Transform.of(new Transform.Function.Translate(
                edge.isVertical() ? zero : length,
                edge.isVertical() ? length : zero)));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                .direction(FlexDirection.COLUMN);
    }
}
