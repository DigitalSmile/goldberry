package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
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

    /// How far a submenu sits from the menu it came from.
    ///
    /// Small on purpose: far enough that the two panels do not share an edge —
    /// which reads as one panel with a seam — and near enough that the pointer
    /// crossing the gap does not leave both menus and put the submenu away
    /// (ADR-0113).
    private static final float SUBMENU_GAP = 2;

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
        // One decision for the whole menu: the leading column appears when
        // *anything* in it has something to put there — a tick or an icon — and
        // then every row has one, so the labels line up. A menu with neither has
        // no column at all, which is most menus and which is the unexplained
        // indent this removes (ADR-0113).
        var reserve = menu.children().stream()
                .anyMatch(child -> child instanceof Item item
                        && (item.isCheckable() || item.icon() != null));
        var children = new ArrayList<Widget>(menu.children().size());
        for (var index = 0; index < menu.children().size(); index++) {
            children.add(prepare(host, menu.children().get(index), index, reserve, self, stack));
        }

        var opened = host.popup(
                fitted(host, menu.withAttributes(menu.attributes()).children(children)),
                anchor, placement);
        opened.ifPresent(popup -> {
            self[0] = popup;
            stack.add(popup);
        });
        return opened;
    }

    /// The menu, in a viewport if it would otherwise be taller than the screen.
    ///
    /// Until `scroll` existed the answer was [Placement]'s: a popup taller than
    /// the work area was clamped to the near edge, which keeps the top visible
    /// and silently drops everything below it. A menu that loses its last three
    /// commands with no indication that it has is the worst kind of wrong, and it
    /// was the honest thing to do with no viewport
    /// ([ADR-0104](../../../../../../../book/src/adr/0104-a-popup-is-measured-then-placed.md)).
    ///
    /// Now it becomes a menu of the screen's height with the items scrolling
    /// inside it. The cap is applied **here** rather than in the popup facility
    /// because `:core` has no widgets to wrap anything in
    /// ([ADR-0092](../../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md))
    /// — and because whether long content should scroll or be clamped is a fact
    /// about the *content*: a tooltip that scrolled would be absurd
    /// ([ADR-0118](../../../../../../../book/src/adr/0118-a-popup-that-does-not-fit-scrolls.md)).
    ///
    /// **Nothing happens to a menu that fits**, which is nearly all of them: the
    /// wrapper is added only when the height is actually exceeded, so an ordinary
    /// menu has no viewport in it and no thumb to fade.
    private static Widget fitted(Host host, Menu menu) {
        var available = host.placeableArea().height() - MARGIN * 2;
        // A conservative estimate rather than a measurement: `Menus` cannot lay
        // anything out, and the popup facility that can does not know what a
        // menu row costs. Erring towards wrapping is the safe direction -- a
        // viewport over content that fits draws no thumb and takes no input.
        var estimate = menu.children().size() * ROW_ESTIMATE + PANEL_CHROME;
        if (estimate <= available) {
            return menu;
        }
        return new Scroll(List.of(menu), ScrollAxis.VERTICAL,
                Attributes.NONE.classes("menu-viewport")).height(available);
    }

    /// How much of the work area a menu leaves alone at each end.
    ///
    /// A menu flush against the top and bottom of the screen looks like a menu
    /// that has been cut off even when it has not.
    private static final float MARGIN = 8;

    /// What one row is assumed to cost, for the estimate in [#fitted].
    ///
    /// `--gb-menu-item-height` is 32 at the regular density and this is not that
    /// token, because a widget cannot read one — the same gap
    /// [ADR-0117](../../../../../../../book/src/adr/0117-a-widget-may-be-told-what-it-measured.md)
    /// records for a wheel line. Rounded **up**, so the estimate errs towards
    /// wrapping a menu that would have fitted rather than clamping one that does
    /// not.
    private static final float ROW_ESTIMATE = 34;

    /// The panel's own padding and border, on both edges.
    private static final float PANEL_CHROME = 16;

    /// Rewrites one child of a menu so that it can do the two things only the
    /// opener can arrange: close the stack when it is chosen, and open its own
    /// submenu.
    private static Widget prepare(Host host, Widget child, int index, boolean reserveLead,
            Popup[] owner, List<Popup> stack) {

        if (!(child instanceof Item item)) {
            return child;
        }
        item = item.reservingLead(reserveLead);
        // An id, because a submenu is anchored to the *item*, and an anchor is
        // looked up by id. Generated rather than required: an author writing a
        // menu should not have to name every row that happens to lead somewhere.
        var identified = item.attributes().id() == null
                ? item.id("item-" + index)
                : item;
        // Every row is told when the pointer arrives on it, because a submenu is
        // closed by the pointer moving to a *sibling* — and most siblings have no
        // submenu of their own (ADR-0112).
        var hovering = identified.hasSubmenu()
                ? identified.hovering(() -> intend(host, identified, owner, stack))
                : identified.hovering(() -> intend(host, null, owner, stack));
        if (hovering.hasSubmenu()) {
            return hovering;
        }
        var command = hovering.onPress();
        return hovering.pressing(() -> {
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

    /// What the pointer arriving on a row means, after [#HOVER_INTENT].
    ///
    /// One timer for both halves, because they are one gesture: travelling down a
    /// menu past three rows with submenus opens none of them, and arriving on a
    /// row *without* one closes whatever the row above had opened. `item` is the
    /// row's submenu to open, or null for "there is nothing here — put away what
    /// is showing".
    ///
    /// A keyboard `Right` goes through here too and waits the same 150ms, which is
    /// wrong and is one line to fix when `Item` can tell the two apart.
    private static void intend(Host host, Item item, Popup[] owner, List<Popup> stack) {
        if (pending != null) {
            pending.cancel();
        }
        pending = host.after(HOVER_INTENT, () -> {
            pending = null;
            if (item == null) {
                collapse(owner, stack);
            } else {
                openSubmenu(host, item, owner, stack);
            }
        });
    }

    /// Closes everything this menu had open, leaving the menu itself.
    ///
    /// What the pointer moving to a row with no submenu means: the branch it was
    /// on is no longer the branch it is on.
    private static void collapse(Popup[] owner, List<Popup> stack) {
        var parent = owner[0];
        if (parent != null && parent.isOpen()) {
            closeAfter(stack, parent);
        }
    }

    /// Opens one item's submenu, beside it, having closed whatever else this menu
    /// had open.
    private static void openSubmenu(Host host, Item item, Popup[] owner, List<Popup> stack) {
        var parent = owner[0];
        if (parent == null || !parent.isOpen()) {
            return;
        }
        var row = parent.anchor(item.attributes().id());
        if (row.isEmpty()) {
            return;
        }
        // Everything this menu opened, closed: moving down past three items with
        // submenus should leave one open, not three.
        closeAfter(stack, parent);

        // **Beside the menu, level with the row.** Two rectangles, because the
        // two axes answer to different things: an item's right edge is a few
        // pixels inside the menu's — the panel's padding and its border — so a
        // submenu anchored to the item alone opens *on top of* the border of the
        // menu it came from, which is what it looked like (ADR-0113).
        var menu = parent.bounds();
        var beside = new io.github.digitalsmile.goldberry.backend.LogicalRect(
                new io.github.digitalsmile.goldberry.backend.LogicalPoint(
                        menu.left(), row.get().top()),
                new io.github.digitalsmile.goldberry.backend.LogicalSize(
                        menu.width(), row.get().height()));

        // `AFTER` and not `BELOW`: a submenu sits beside its menu, and flips to
        // the other side near the edge of the screen, which is `Placement`'s
        // (ADR-0104). The gap is the couple of pixels that keep the two panels
        // from sharing an edge.
        open(host, beside, new Menu(item.submenu(), item.attributes().id(null)),
                Placement.AFTER.gap(SUBMENU_GAP), stack);
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
