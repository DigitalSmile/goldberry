package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// What a [Collapse] shows when it is open — §5's "region".
///
/// A node of its own rather than the author's children going straight into the
/// section, so that a stylesheet can indent the body under the header without
/// also indenting the header. `slider`'s anatomy argument again: two boxes were
/// doing one job.
///
/// **It only exists while the section is open.** `CollapseSection` does not
/// describe one otherwise, so there is no element, nothing mounted and nothing
/// subscribed — see [Collapse]'s note.
record CollapseBody(List<Widget> children) implements Widget.Leaf, Styled, Paints {

    CollapseBody {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public String cssType() {
        return "collapse-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
