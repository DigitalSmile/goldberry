package dev.goldberry.widgets.menu;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.FocusScope;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A column of rows: the value a context menu, a tray and a menu bar heading
/// all hold.
///
/// ```kdl
/// menu id="row-menu" {
///     item press="app.rename" "Rename"
///     item press="app.duplicate" "Duplicate"
///     separator
///     item press="app.delete" "Delete"
/// }
/// ```
///
/// ```java
/// var rowMenu = new Menu(new Item("Rename", actions::rename), new Separator(), new Item("Delete", actions::delete));
/// Menus.open(host, "more-button", rowMenu);
/// ```
///
/// A menu is a widget and opening one is not. This is the panel and its rows:
/// a document can write it, a stylesheet can style it, and it draws in
/// whatever it is put in. **Opening** it — measuring, placing it against the
/// thing that summoned it, opening a platform window, closing it again — is
/// [Menus], because that needs a `Host` and a widget is a value. The children
/// are [Item]s and [Separator]s; when any row has an icon or is checkable,
/// every row reserves a lead column so the labels line up.
///
/// A **vertical focus scope**: `Up` and `Down` move between rows, and `Left`
/// and `Right` are left to the rows, where they mean "close this submenu" and
/// "open that one". `Escape` closes this menu and no more, and belongs to the
/// popup rather than to any row.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menu).
///
/// @param children   the items, separators and anything else a menu is made of
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("menu")
public record Menu(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Menu> {

    /// Written out so that the parameters taking null for a default can say so.
    public Menu(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        Objects.requireNonNull(children, "children");
        this.children = children;
        this.attributes = attributes;
    }

    public Menu(Widget... children) {
        this(List.of(children), Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "menu";
    }

    @Override
    public @Nullable String id() {
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

    /// Builds a `menu` from markup.
    ///
    /// A document declares a menu; *opening* one is `Menus.open(host, …)`,
    /// because that needs a `Host` and a widget must not have one.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Menu(children, Attributes.of(node));
    }
}
