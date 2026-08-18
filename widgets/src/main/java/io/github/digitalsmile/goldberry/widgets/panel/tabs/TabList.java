package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The row of headers in a [Tabs] — a **part**, and the thing the bottom rule is
/// drawn on.
///
/// It exists for `segmented-track`'s reason: two boxes were doing one job. The
/// strip's own box carries the whole control, headers *and* panel; the rule under
/// the headers has to stop where the headers do, and a border on the outer box
/// would be under the panel as well.
///
/// The rule it carries is a [TabRule] rather than a `border-bottom`, because §8's
/// subset has no per-edge borders — see [TabIndicator].
///
/// @param headers the tabs, already told which of them is selected
record TabList(List<Widget> headers) implements Widget.Leaf, Styled, Paints {

    TabList {
        headers = List.copyOf(headers == null ? List.of() : headers);
    }

    @Override
    public String cssType() {
        return "tab-list";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// The rule **first**, so the headers and their indicators are painted over
    /// it — a box tree has no z-order beyond document order (ADR-0053).
    @Override
    public List<Widget> children() {
        var children = new java.util.ArrayList<Widget>(headers.size() + 1);
        children.add(new TabRule());
        children.addAll(headers);
        return List.copyOf(children);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
