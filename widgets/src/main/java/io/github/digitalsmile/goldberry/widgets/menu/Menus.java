package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Opens a [Menu] — the half of `docs/core-widgets.md` §8 that is not a widget.
///
/// ```java
/// Menus.open(host, "file-button", new Menu(
///         new Item("Open…", this::open).accelerator("Ctrl+O"),
///         new Separator(),
///         new Item("Recent").submenu(
///                 new Item("notes.txt", () -> open("notes.txt")))));
/// ```
///
/// ## Why this is not on the widget
///
/// Opening a menu needs a `Host`: something has to measure the panel, place it
/// against whatever summoned it, ask the platform for a window and close it
/// again. A widget has no `Host` and must not — it is a value, described afresh
/// every frame, and one holding the window it is drawn in would be describing its
/// own surroundings ([ADR-0106]).
///
/// So a menu is a widget and opening one is a call, and the two meet the way a
/// composite meets its children everywhere else in this catalog: the opener
/// rebuilds the tree, handing each item the thing only the opener knows — here, a
/// way to open its own submenu, exactly as a `radio-group` hands each `radio` its
/// `selected` and its `onSelect`.
///
/// ## What closes what
///
/// Every command **closes the whole stack**, which is what a menu does everywhere:
/// choosing "Save" from a submenu of a submenu leaves nothing on screen. A press
/// outside, or `Escape`, does the same through the popup's own light dismissal
/// ([ADR-0103]).
///
/// A submenu closes its siblings as it opens, so moving down a menu past three
/// items with submenus leaves one open rather than three.
public final class Menus {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(Menus.class);

    private Menus() {
    }

    /// How long the pointer has to rest on a row before its submenu opens —
    /// §8's "hover-intent timing".
    ///
    /// Short, because a submenu is what the pointer is *going* to, unlike a
    /// tooltip which is a thing it happened to stop on. Long enough that
    /// travelling down a menu past three rows with submenus opens none of them.
    private static final java.time.Duration HOVER_INTENT = java.time.Duration.ofMillis(150);

    /// Wires up §8's context menus: `context-menu="rowMenu"` on any widget opens
    /// `menus.get("rowMenu")` where the pointer is.
    ///
    /// One line in an application, and the line is here rather than in `:core`
    /// because the two halves of a context menu live on opposite sides of the
    /// catalog boundary. The toolkit notices the right-click and knows which name
    /// the widget carried; only the catalog can turn that name into a menu and
    /// open it, which means wrapping every item so that choosing it closes the
    /// stack ([ADR-0108](../../../../../../../book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)).
    ///
    /// A name nobody registered is **logged and ignored**, not thrown: a right
    /// click is not a request that can fail usefully, and taking a window down
    /// because a menu is missing is worse than the menu being missing.
    ///
    /// @param host  the window the menus open on
    /// @param menus what each name means. Read on every right-click, so a map that
    ///              changes is a set of menus that changes
    public static void contextMenus(Host host, java.util.Map<String, Menu> menus) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(menus, "menus");
        host.onContextMenu((menuId, at) -> {
            var menu = menus.get(menuId);
            if (menu == null) {
                LOG.warn("no context menu is registered as \"{}\"; known: {}",
                        menuId, menus.keySet());
                return;
            }
            open(host, at, menu);
        });
    }

    /// Opens `menu` under the node with `anchorId`, in the window's own tree.
    ///
    /// Empty when the platform has no popup windows or nothing with that id was
    /// painted — see [Host#popup(Widget, String, Placement)].
    public static Optional<Popup> open(Host host, String anchorId, Menu menu) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(anchorId, "anchorId");
        return host.anchor(anchorId).flatMap(anchor -> open(host, anchor.bounds(), menu));
    }

    /// Opens `menu` against a rectangle in the window's own coordinates — where a
    /// context menu goes, the rectangle being the point the pointer was at.
    public static Optional<Popup> open(Host host, LogicalRect anchor, Menu menu) {
        return open(host, anchor, menu, Placement.BELOW, new ArrayList<>());
    }

    /// The real one. `stack` is every popup opened from this root, so a command
    /// can close all of them.
    private static Optional<Popup> open(Host host, LogicalRect anchor, Menu menu,
            Placement placement, List<Popup> stack) {

        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(menu, "menu");

        // The submenu opener needs the popup, and the popup needs the tree that
        // the opener is being written into. One box, filled in as soon as the
        // popup exists -- which is before anything can be hovered, let alone
        // clicked.
        var self = new Popup[1];
        var children = new ArrayList<Widget>(menu.children().size());
        for (var index = 0; index < menu.children().size(); index++) {
            children.add(prepare(host, menu.children().get(index), index, self, stack));
        }

        var opened = host.popup(menu.withAttributes(menu.attributes()).children(children),
                anchor, placement);
        opened.ifPresent(popup -> {
            self[0] = popup;
            stack.add(popup);
        });
        return opened;
    }

    /// Rewrites one child of a menu so that it can do the two things only the
    /// opener can arrange: close the stack when it is chosen, and open its own
    /// submenu.
    private static Widget prepare(Host host, Widget child, int index, Popup[] owner,
            List<Popup> stack) {

        if (!(child instanceof Item item)) {
            return child;
        }
        // An id, because a submenu is anchored to the *item*, and an anchor is
        // looked up by id. Generated rather than required: an author writing a
        // menu should not have to name every row that happens to lead somewhere.
        var identified = item.attributes().id() == null
                ? item.id("item-" + index)
                : item;
        if (identified.hasSubmenu()) {
            return identified.opensWith(() -> intendSubmenu(host, identified, owner, stack));
        }
        var command = identified.onPress();
        return identified.pressing(() -> {
            closeAll(stack);
            if (command != null) {
                command.run();
            }
        });
    }

    /// The pending hover intent, cancelled by the next hover.
    ///
    /// Static because there is one pointer: two menus cannot both be being
    /// hovered, and a per-menu timer would let a submenu open after the pointer
    /// had already moved to a different menu entirely.
    private static io.github.digitalsmile.goldberry.backend.EventLoop.Timer pending;

    /// Waits [#HOVER_INTENT] before opening, so travelling down a menu past three
    /// rows with submenus opens none of them.
    ///
    /// A keyboard `Right` goes through here too and waits the same 150ms, which is
    /// wrong and is one line to fix when `Item` can tell the two apart.
    private static void intendSubmenu(Host host, Item item, Popup[] owner, List<Popup> stack) {
        if (pending != null) {
            pending.cancel();
        }
        pending = host.after(HOVER_INTENT, () -> {
            pending = null;
            openSubmenu(host, item, owner, stack);
        });
    }

    /// Opens one item's submenu, beside it, having closed whatever else this menu
    /// had open.
    private static void openSubmenu(Host host, Item item, Popup[] owner, List<Popup> stack) {
        var parent = owner[0];
        if (parent == null || !parent.isOpen()) {
            return;
        }
        var anchor = parent.anchor(item.attributes().id());
        if (anchor.isEmpty()) {
            return;
        }
        // Everything this menu opened, closed: moving down past three items with
        // submenus should leave one open, not three.
        closeAfter(stack, parent);
        // `AFTER` and not `BELOW`: a submenu sits beside its item, and flips to
        // the other side near the edge of the screen, which is `Placement`'s
        // (ADR-0104).
        open(host, anchor.get(), new Menu(item.submenu(), item.attributes().id(null)),
                Placement.AFTER, stack);
    }

    private static void closeAll(List<Popup> stack) {
        for (var popup : List.copyOf(stack)) {
            popup.close();
        }
        stack.clear();
    }

    /// Closes everything opened after `keep`, leaving `keep` and its ancestors.
    private static void closeAfter(List<Popup> stack, Popup keep) {
        var index = stack.indexOf(keep);
        if (index < 0) {
            return;
        }
        for (var popup : List.copyOf(stack.subList(index + 1, stack.size()))) {
            popup.close();
            stack.remove(popup);
        }
    }
}
