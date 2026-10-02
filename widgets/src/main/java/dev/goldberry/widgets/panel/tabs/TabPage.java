package dev.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One kept tab's content in a `keep-alive` strip — a **part**, `tab-page`.
///
/// Keyed by the tab's value, so the reconciler matches a page to the same
/// elements whichever tab is selected, and **hidden** rather than absent while
/// another tab is showing: its elements, and every state under them, stay
/// mounted and take no layout, paint, input or focus.
///
/// @param value    the tab this is the content of
/// @param content  that content
/// @param selected whether it is the tab being shown
record TabPage(String value, List<Widget> content, boolean selected) implements Widget.Leaf, Styled, Paints {

    TabPage {
        content = List.copyOf(content);
    }

    @Override
    public String cssType() {
        return "tab-page";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Object key() {
        return value;
    }

    @Override
    public boolean isHidden() {
        return !selected;
    }

    @Override
    public List<Widget> children() {
        return content;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
