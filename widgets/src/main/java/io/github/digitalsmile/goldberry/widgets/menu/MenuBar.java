package io.github.digitalsmile.goldberry.widgets.menu;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// An in-window menu bar — `docs/core-widgets.md` §8's `menubar`.
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
/// ## There is no new markup, because a nested `item` is already a submenu
///
/// A bar's children are [Item]s, and an item with items inside it is a heading
/// that opens a menu — which is the syntax [Menus] has used since ADR-0106. So a
/// `menubar` is a row of the thing a menu was already made of, and nothing about
/// declaring one is new to learn.
///
/// ## What outlives an opening
///
/// ADR-0106 recorded why the accelerator's second half was not built: "a shortcut
/// has to work while the menu is shut, and a menu is built when it opens and
/// thrown away when it closes". What is thrown away is the **popup**. The
/// [Menu] is a value — the author's own description, held here for as long as
/// this element is mounted — so a bar registers every accelerator underneath it
/// on mount and gives them back on unmount, and `Ctrl+O` works with nothing on
/// screen ([ADR-0163], [Accelerators]).
///
/// ## The keyboard
///
/// `Tab` reaches the bar and `Left`/`Right` walk it, because [MenuBarRow] is a
/// horizontal focus scope. `Enter`, `Space` or `Down` open the heading's menu,
/// and the menu's own `Up`/`Down` take over from there.
///
/// **A bare `Alt` tap opens the bar, and so does `F10`.** The `Alt` half is §8's
/// "`Alt`-style keyboard activation" itself rather than a stand-in for it: a tap
/// is a *modifier* released with nothing in between, which is a gesture over two
/// events and not a [io.github.digitalsmile.goldberry.input.key.Shortcut] — `Key`
/// still has no `ALT` to name, deliberately, because an accelerator on a modifier
/// alone could never fire. What recognises the gesture, and everything that
/// spoils it, is [io.github.digitalsmile.goldberry.input.tap.ModifierTaps]
/// ([ADR-0223]). `F10` stays beside it: it is the companion binding on every
/// platform that has the `Alt` one, and it is the only one that survives a
/// compositor that eats `Alt` for its own window switcher.
///
/// Either key **closes** an open bar, which is what every desktop does with them.
///
/// @param children   the headings, each an [Item] with a submenu
/// @param attributes the `id` and classes, which land on the `menubar` node
@Markup("menubar")
public record MenuBar(List<Widget> children, Attributes attributes) implements Widget.Stateful, Attributed<MenuBar> {

    public MenuBar {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
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
