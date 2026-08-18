package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// A list of commands — `docs/core-widgets.md` §8's `menu`.
///
/// ```kdl
/// menu id="file" {
///     item press="app.open" icon="folder" accelerator="Ctrl+O" "Open…"
///     separator
///     item press="app.quit" accelerator="Ctrl+Q" "Quit"
/// }
/// ```
///
/// ```java
/// Menus.open(host, "file-button", menu);
/// ```
///
/// ## A menu is a widget; opening one is not
///
/// This is the panel and its items, exactly as `popover` is a panel
/// ([ADR-0104](../../../../../../../book/src/adr/0104-a-popup-is-measured-then-placed.md)):
/// a document can write it, a stylesheet can style it, and it draws in whatever
/// it is put in. **Opening** it — measuring, placing it against the thing that
/// summoned it, opening a platform window, closing it again — is [Menus], because
/// it needs a `Host` and a widget must not have one
/// ([ADR-0106](../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)).
///
/// ## The keyboard
///
/// A **vertical focus scope**: `Up` and `Down` move between items and `Left` and
/// `Right` are left alone, because in a menu they mean "close this submenu" and
/// "open that one" — which is [Item]'s business and not a traversal
/// ([ADR-0078](../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)).
/// `Escape` closes the whole thing and belongs to the popup, not to any item.
///
/// @param children   the items, separators and anything else a menu is made of
/// @param attributes `id` and `class`, exactly as on the primitives
public record Menu(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Menu> {

    public Menu {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        Objects.requireNonNull(children, "children");
    }

    public Menu(Widget... children) {
        this(List.of(children), Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "menu";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    /// This menu with different children — what [Menus] builds when it hands each
    /// item the thing only an opener knows.
    public Menu children(List<Widget> value) {
        return new Menu(value, attributes);
    }

    @Override
    public Menu withAttributes(Attributes value) {
        return new Menu(children, value);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    /// Vertical, and the horizontal half is deliberately absent — see the class
    /// note.
    @Override
    public FocusScope focusScope() {
        return FocusScope.VERTICAL;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
