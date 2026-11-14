package dev.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The node a stylesheet calls `list-section`: one section of a list that
/// builds every row, its header in an `affix` and its rows under it.
///
/// The section is the box the header is pinned inside, which is what carries
/// the header out when the section's last row leaves and the next header
/// arrives.
///
/// @param section  the section's key
/// @param children the header's affix, then the rows
record ListSection(String section, List<Widget> children) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "list-section";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Object key() {
        return "list-section:" + section;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).direction(FlexDirection.COLUMN).children(boxes.toArray(Box[]::new));
    }
}
