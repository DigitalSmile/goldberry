package dev.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The hairline under a [TabList] — a **part**, and a box.
///
/// A box rather than a `border-bottom` on the list, because a border on the
/// list is drawn before the tabs, and the indicator has to lie over this line.
///
/// It runs the full width of the strip and the selected tab's indicator is drawn
/// *over* it, which is what makes a tab read as attached to its panel. Both are
/// absolutely positioned and the indicator is listed after, because a box tree
/// has no z-order beyond document order.
record TabRule() implements Widget.Leaf, Styled, Paints {

    private static final Insets PINNED =
            new Insets(Length.UNDEFINED, Length.points(0), Length.points(0), Length.points(0));

    @Override
    public String cssType() {
        return "tab-rule";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).position(Position.ABSOLUTE).inset(PINNED);
    }
}
