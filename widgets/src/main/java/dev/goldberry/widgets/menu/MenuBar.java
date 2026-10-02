package dev.goldberry.widgets.menu;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A horizontal bar of headings, each an [Item] whose children are its menu.
///
/// ```kdl
/// menubar {
///     item "File" {
///         item press="app.open" accelerator="Ctrl+O" "Open…"
///         separator
///         item press="app.quit" accelerator="Ctrl+Q" "Quit"
///     }
///     item "Edit" {
///         item press="app.undo" accelerator="Ctrl+Z" "Undo"
///     }
/// }
/// ```
///
/// ```java
/// new MenuBar(
///         new Item("File").submenu(
///                 new Item("Open…", this::open).accelerator("Ctrl+O"),
///                 new Separator(),
///                 new Item("Quit", this::quit).accelerator("Ctrl+Q")),
///         new Item("Edit").submenu(
///                 new Item("Undo", this::undo).accelerator("Ctrl+Z")));
/// ```
///
/// A nested `item` is already a submenu, so a bar is a row of the thing a menu
/// is made of and there is no new markup to learn. A child that is not an item
/// is placed in the row unchanged. A menu bar is a widget and not a property
/// of the window, so a screen may have a second one.
///
/// The bar owns its menus. It opens a heading's menu as a popup against the
/// heading, swaps to the neighbour when the pointer runs along the bar with a
/// menu down, and binds every accelerator its rows name while it is mounted,
/// giving them back on unmount, so `Ctrl+O` works with nothing on screen. The
/// [Menu] is a value held for as long as the bar is, which is what lets the
/// keys outlive any one opening of a popup.
///
/// `Tab` reaches the bar and `Left`/`Right` walk it. `Enter`, `Space` or
/// `Down` open the focused heading, and the menu's own arrows take over from
/// there. `F10`, or a tap on a bare `Alt`, opens the first heading and closes
/// the bar when a menu is open, as every desktop does. A tap is a modifier
/// released with nothing in between, a gesture over two events rather than a
/// shortcut, so it has a registration of its own. There are no mnemonics.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menubar).
///
/// @param children   the headings, each an [Item] with a submenu
/// @param attributes the `id` and classes, which land on the `menubar` node
@Markup("menubar")
public record MenuBar(List<Widget> children, Attributes attributes) implements Widget.Stateful, Attributed<MenuBar> {

    /// Written out so that the parameters taking null for a default can say so.
    public MenuBar(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
    }

    public MenuBar(Widget... children) {
        this(List.of(children), Attributes.NONE);
    }

    @Override
    public MenuBar withAttributes(Attributes value) {
        return new MenuBar(children, value);
    }

    @Override
    public State<?> createState() {
        return new MenuBarState();
    }

    /// Builds a `menubar` from markup. Its children are `item`s, and an `item`
    /// with `item`s inside it is a heading — the same nesting a submenu uses.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new MenuBar(children, Attributes.of(node));
    }
}
