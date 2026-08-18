package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The hairline under a [TabList] — a **part**, and a box for
/// [TabIndicator]'s reason: there is no `border-bottom` in §8's subset.
///
/// It runs the full width of the strip and the selected tab's indicator is drawn
/// *over* it, which is what makes a tab read as attached to its panel. Both are
/// absolutely positioned and the indicator is listed after, because a box tree
/// has no z-order beyond document order (ADR-0053).
record TabRule() implements Widget.Leaf, Styled, Paints {

    private static final Insets PINNED = new Insets(
            StyleLength.UNDEFINED, StyleLength.points(0),
            StyleLength.points(0), StyleLength.points(0));

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
        return Box.of().style(style).position(PositionType.ABSOLUTE).inset(PINNED);
    }
}
