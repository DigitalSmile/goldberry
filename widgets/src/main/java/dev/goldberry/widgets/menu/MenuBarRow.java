package dev.goldberry.widgets.menu;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.FocusScope;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The `menubar` a stylesheet selects.
///
/// [MenuBar] is stateful and styles nothing — it owns the open popup and the
/// accelerator registrations — so this node carries the CSS type and the `id`
/// and classes the document wrote, the same split `select` and `tabs` use. A
/// part in every other respect: not registered for markup, because nobody
/// writes it.
///
/// A **horizontal** focus scope, where a [Menu] is a vertical one: `Left` and
/// `Right` walk the headings, and `Up` and `Down` are left alone, `Down`
/// because [MenuTitle] spends it on opening the menu below. One tab stop, like
/// every other composite: `Tab` reaches the bar, the arrows move within it,
/// and `Tab` again leaves it.
record MenuBarRow(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints, Handles {

    /// Written out so that the parameters taking null for a default can say so.
    MenuBarRow(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "menubar";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public FocusScope focusScope() {
        return FocusScope.HORIZONTAL;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
