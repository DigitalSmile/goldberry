package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// **This is the `menubar` a stylesheet selects.**
///
/// [MenuBar] is stateful and styles nothing — it owns the open popup and the
/// accelerator registrations — so this node carries the CSS type and the `id`
/// and classes the document wrote. The same split `select` and `tabs` already
/// use, and the reason markup parity is checked against what a widget
/// *describes* rather than against the widget
/// ([ADR-0116](../../../../../../../book/src/adr/0116-a-widget-is-checked-against-the-markup-it-claims.md)).
///
/// A part in every other respect: not registered for markup, because
/// `menubar-row` is not a node anybody writes.
///
/// ## Horizontal, which is the only interesting thing about it
///
/// A [Menu] is a **vertical** focus scope so that `Left` and `Right` stay free
/// for its submenus. A bar is the other one: `Left` and `Right` walk the
/// headings, and `Up` and `Down` are what it leaves alone — `Down` because
/// [MenuTitle] spends it on opening the menu below, which is where the menu is
/// ([ADR-0078](../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)).
///
/// One tab stop, like every other composite here: `Tab` reaches the bar, the
/// arrows move within it, and `Tab` again leaves it
/// ([ADR-0073](../../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
record MenuBarRow(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    MenuBarRow {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "menubar";
    }

    @Override
    public String id() {
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
