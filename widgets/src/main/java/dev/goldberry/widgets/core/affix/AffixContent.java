package dev.goldberry.widgets.core.affix;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.value.Transform;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The part of an [Affix] that actually moves.
///
/// A translate, for `scroll-content`'s reason: it costs no layout, so a header
/// that stays put while a thousand rows scroll under it re-runs Yoga exactly
/// never. It is also what keeps the hole above it the size it was — a margin or
/// an inset would move the hole too, which is the one thing §1 says must not
/// happen.
record AffixContent(List<Widget> children, double shiftX, double shiftY) implements Widget.Leaf, Styled, Paints {

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
    AffixContent(@Nullable List<Widget> children, double shiftX, double shiftY) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        if (shiftX == 0 && shiftY == 0) {
            return resolved;
        }
        return resolved.transform(Transform.of(
                new Transform.Function.Translate(Transform.Length.px(shiftX), Transform.Length.px(shiftY))));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style).direction(FlexDirection.COLUMN);
    }
}
