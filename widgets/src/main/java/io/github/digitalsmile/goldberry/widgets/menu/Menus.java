package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Fitted;
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
        return open(host, anchorId, menu, Placement.BELOW);
    }

    /// [#open(Host, String, Menu)] somewhere other than straight below.
    ///
    /// `menubar` is what wanted this: a heading's menu hangs from the bar with no
    /// gap, where a context menu stands off the pointer so as not to open
    /// underneath it (ADR-0163).
    public static Optional<Popup> open(Host host, String anchorId, Menu menu,
            Placement placement) {

        return open(host, anchorId, menu, placement, null);
    }

    /// The same, told what the arrows that **leave** this menu mean.
    ///
    /// Only a menu bar has an answer: `Left` from the root of a `File` menu goes
    /// to whatever is on its left, which is a fact about the bar and not about
    /// the menu. Everything else passes null, and those two arrows do nothing at
    /// the root of a context menu — which is right, because there is nowhere to
    /// go ([ADR-0219](../../../../../../../book/src/adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md)).
    public static Optional<Popup> open(Host host, String anchorId, Menu menu,
            Placement placement, Siblings siblings) {

        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(anchorId, "anchorId");
        Objects.requireNonNull(placement, "placement");
        return host.anchor(anchorId)
                .flatMap(anchor -> open(host, anchor.bounds(), menu, placement,
                        new ArrayList<>(), null, siblings));
    }

    /// Opens `menu` against a rectangle in the window's own coordinates — where a
    /// context menu goes, the rectangle being the point the pointer was at.
    public static Optional<Popup> open(Host host, LogicalRect anchor, Menu menu) {
        return open(host, anchor, menu, Placement.BELOW, new ArrayList<>(), null, null);
    }

    /// What the two arrows that leave a menu mean to whoever opened it.
    ///
    /// A `menubar` supplies one and nothing else does. Both halves are optional:
    /// a bar with one menu on it has neither a previous nor a next.
    ///
    /// @param previous `Left` at the root of this menu — the menu on the left
    /// @param next     `Right` from a row with no submenu — the menu on the right
    public record Siblings(Runnable previous, Runnable next) {

        void previousMenu() {
            if (previous != null) {
                previous.run();
            }
        }

        void nextMenu() {
            if (next != null) {
                next.run();
            }
        }
    }

    /// The real one. `stack` is every popup opened from this root, so a command
    /// can close all of them; `parent` is the menu this one hangs off, or null
    /// for the root; `siblings` is a bar's two arrows, or null.
    private static Optional<Popup> open(Host host, LogicalRect anchor, Menu menu,
            Placement placement, List<Popup> stack, OpenMenu parent, Siblings siblings) {

        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(menu, "menu");

        var open = new OpenMenu(host, menu, stack, parent, siblings);
        var opened = host.popup(
                menu.withAttributes(menu.attributes()).children(open.describe()),
                anchor, placement, 0, VIEWPORT);
        opened.ifPresent(popup -> {
            open.self = popup;
            stack.add(popup);
        });
        return opened;
    }

    /// A menu longer than the screen becomes a menu of the screen's height with
    /// its items scrolling inside it.
    ///
    /// This used to be a **conservative estimate** applied here before the popup
    /// was opened — rows times an assumed 34px — because `Menus` cannot lay
    /// anything out and the facility that can did not say what it measured. It
    /// erred towards wrapping, so a menu that would have fitted could get a
    /// viewport nobody could see; and `--gb-menu-item-height` being 32 rather
    /// than 34 was a number kept in two places, one of which was a stylesheet a
    /// widget cannot read.
    ///
    /// It is a measurement now. The decision is still made **here** rather than
    /// in the popup facility, for the two reasons that have not changed: `:core`
    /// has no widgets to wrap anything in
    /// ([ADR-0092](../../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)),
    /// and whether long content should scroll or be clamped is a fact about the
    /// content — a tooltip that scrolled would be absurd
    /// ([ADR-0118](../../../../../../../book/src/adr/0118-a-popup-that-does-not-fit-scrolls.md),
    /// [ADR-0179](../../../../../../../book/src/adr/0179-a-popup-says-what-it-measured.md)).
    ///
    /// **Nothing happens to a menu that fits**, which is nearly all of them.
    private static final Fitted VIEWPORT = new Fitted("menu-viewport");

    /// The pending hover intent, cancelled by the next hover.
    ///
    /// Static because there is one pointer: two menus cannot both be being
    /// hovered, and a per-menu timer would let a submenu open after the pointer
    /// had already moved to a different menu entirely.
    private static EventLoop.Timer pending;

    private static void cancelPending() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
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

    /// One menu that is on screen: the popup it became, the rows in it, and which
    /// of them has its submenu showing.
    ///
    /// It exists because four of `TODO.md`'s entries were the same missing thing —
    /// a row could tell its menu that the pointer had arrived and nothing else, so
    /// a keyboard `Right` waited out the pointer's delay, `Left` closed nothing,
    /// and the row whose branch was open looked like every other row. All four are
    /// answers only the menu can give, and this is the menu
    /// ([ADR-0219](../../../../../../../book/src/adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md)).
    private static final class OpenMenu {

        private final Host host;
        private final Menu menu;

        /// Every popup opened from this root, shared with the whole stack.
        private final List<Popup> stack;

        /// The menu this one hangs off, or null if it is the root.
        private final OpenMenu parent;

        /// A bar's two arrows, or null for a menu that is not a bar's.
        private final Siblings siblings;

        /// The popup this menu became. Filled in as soon as it exists — which is
        /// before anything can be hovered, let alone pressed.
        private Popup self;

        /// Which row's submenu is showing, or -1. Read by [#describe] to mark
        /// that row `.open`, which is the only thing that tells a reader which
        /// branch they are down.
        private int openIndex = -1;

        OpenMenu(Host host, Menu menu, List<Popup> stack, OpenMenu parent, Siblings siblings) {
            this.host = host;
            this.menu = menu;
            this.stack = stack;
            this.parent = parent;
            this.siblings = siblings;
        }

        /// This menu's rows, wired to it.
        ///
        /// Called again whenever the open branch changes, and handed to
        /// [Popup#content] — which reconciles from the root, so the keyboard keeps
        /// its place and nothing flickers.
        List<Widget> describe() {
            // One decision for the whole menu: the leading column appears when
            // *anything* in it has something to put there -- a tick or an icon --
            // and then every row has one, so the labels line up. A menu with
            // neither has no column at all, which is most menus and which is the
            // unexplained indent this removes (ADR-0113).
            var reserve = menu.children().stream()
                    .anyMatch(child -> child instanceof Item item
                            && (item.isCheckable() || item.icon() != null));
            var children = new ArrayList<Widget>(menu.children().size());
            for (var index = 0; index < menu.children().size(); index++) {
                children.add(prepare(menu.children().get(index), index, reserve));
            }
            return List.copyOf(children);
        }

        /// Rewrites one child so that it can do the things only the menu can
        /// arrange: close the stack when it is chosen, open its own submenu, go
        /// back, and look open while its branch is showing.
        private Widget prepare(Widget child, int index, boolean reserveLead) {
            if (!(child instanceof Item item)) {
                return child;
            }
            item = item.reservingLead(reserveLead);
            // An id, because a submenu is anchored to the *item*, and an anchor
            // is looked up by id. Generated rather than required: an author
            // writing a menu should not have to name every row that happens to
            // lead somewhere.
            if (item.attributes().id() == null) {
                item = item.id("item-" + index);
            }
            if (index == openIndex) {
                // `.open` rather than a pseudo-class, because "the branch that is
                // showing" is not one of CSS's states and inventing one would put
                // a menu's internals in the selector engine. The same shape
                // `menu-title` uses for the heading whose menu is down.
                item = item.styled(withOpen(item.attributes().classes()));
            }
            var row = item;
            var wired = item.signalling(new MenuSignals() {

                @Override
                public void hovered() {
                    intend(row.hasSubmenu() ? row : null, index);
                }

                @Override
                public void open() {
                    // No delay. §8's hover-intent stops a submenu dropping out of
                    // a pointer travelling past three rows, and a keypress has
                    // travelled past nothing.
                    cancelPending();
                    openSubmenu(row, index);
                }

                @Override
                public void back() {
                    goBack();
                }

                @Override
                public void forward() {
                    if (siblings != null) {
                        siblings.nextMenu();
                    }
                }
            });
            if (wired.hasSubmenu()) {
                return wired;
            }
            var command = wired.onPress();
            return wired.pressing(() -> {
                closeAll(stack);
                if (command != null) {
                    command.run();
                }
            });
        }

        private static String[] withOpen(java.util.Set<String> classes) {
            var all = new ArrayList<>(classes);
            all.add("open");
            return all.toArray(String[]::new);
        }

        /// What the pointer arriving on a row means, after [#HOVER_INTENT].
        ///
        /// One timer for both halves, because they are one gesture: travelling
        /// down a menu past three rows with submenus opens none of them, and
        /// arriving on a row *without* one closes whatever the row above had
        /// opened. `item` is the row's submenu to open, or null for "there is
        /// nothing here — put away what is showing".
        private void intend(Item item, int index) {
            cancelPending();
            pending = host.after(HOVER_INTENT, () -> {
                pending = null;
                if (item == null) {
                    collapse();
                } else {
                    openSubmenu(item, index);
                }
            });
        }

        /// Closes everything this menu had open, leaving the menu itself.
        ///
        /// What the pointer moving to a row with no submenu means: the branch it
        /// was on is no longer the branch it is on.
        private void collapse() {
            if (self == null || !self.isOpen()) {
                return;
            }
            closeAfter(stack, self);
            if (openIndex != -1) {
                openIndex = -1;
                self.content(menu.children(describe()));
            }
        }

        /// `Left`: out of this menu, whatever that means where this menu is.
        ///
        /// A submenu goes back to the menu that opened it — which is the arrow
        /// that opened it, undone. The **root** of a bar's menu has no menu to go
        /// back to and a bar to move along instead, and a context menu's root has
        /// neither, so `Left` there does nothing rather than closing the menu: a
        /// menu that vanished on an arrow key would be a menu nobody could
        /// navigate.
        private void goBack() {
            cancelPending();
            if (parent != null) {
                parent.collapse();
            } else if (siblings != null) {
                siblings.previousMenu();
            }
        }

        /// Opens one row's submenu, beside it, having closed whatever else this
        /// menu had open.
        private void openSubmenu(Item item, int index) {
            if (self == null || !self.isOpen()) {
                return;
            }
            var row = self.anchor(item.attributes().id());
            if (row.isEmpty()) {
                return;
            }
            // Everything this menu opened, closed: moving down past three items
            // with submenus should leave one open, not three.
            closeAfter(stack, self);

            // Marked *before* the submenu opens, so the row the branch belongs to
            // is lit in the same frame the branch appears in.
            if (openIndex != index) {
                openIndex = index;
                self.content(menu.children(describe()));
            }

            // **Beside the menu, level with the row.** Two rectangles, because
            // the two axes answer to different things: an item's right edge is a
            // few pixels inside the menu's -- the panel's padding and its border
            // -- so a submenu anchored to the item alone opens *on top of* the
            // border of the menu it came from, which is what it looked like
            // (ADR-0113).
            var bounds = self.bounds();
            var beside = new LogicalRect(
                    new LogicalPoint(bounds.left(), row.get().top()),
                    new LogicalSize(bounds.width(), row.get().height()));

            // `AFTER` and not `BELOW`: a submenu sits beside its menu, and flips
            // to the other side near the edge of the screen, which is
            // `Placement`'s (ADR-0104). The gap is the couple of pixels that keep
            // the two panels from sharing an edge.
            //
            // No `Siblings`: a submenu is not on the bar, so `Left` in it goes
            // back to this menu rather than to the menu on the bar's left.
            open(host, beside, new Menu(item.submenu(), item.attributes().id(null)),
                    Placement.AFTER.gap(SUBMENU_GAP), stack, this, null);
        }
    }
}
