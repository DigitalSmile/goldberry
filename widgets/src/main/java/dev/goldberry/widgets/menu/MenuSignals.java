package dev.goldberry.widgets.menu;

/// What an [Item] can tell the menu it is in.
///
/// An item does not open its own submenu, close the menu it is in, or move to
/// the next menu on a bar: all three need a `Host` and a popup, and a widget is
/// a value. So it is handed a way to *ask*, exactly as a `radio` is handed
/// `selected` and `onSelect` by its group. Every one of these is a thing the
/// *menu* does and the *item* knows about first.
///
/// The signals say what happened, not what to do. [#hovered()] does not mean
/// "open my submenu" — it means "the pointer is on me now", and a row with no
/// submenu sends it too, because that is how the menu knows to put away what
/// the row above opened. [#open()] is the keyboard's deliberate request, which
/// is why the two are separate and why one waits 150 ms and the other does not.
///
/// Every method has a no-op default, so a menu wires up only the signals it can
/// answer: a context menu has no bar to move along, and its rows' [#forward()]
/// does nothing.
///
/// Supplied by [Menus], never by an author.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menu).
public interface MenuSignals {

    /// An item nobody has wired up — every signal does nothing.
    ///
    /// What an `Item` written in a document holds until the menu it is in is
    /// opened, and what a bare item outside any menu keeps. A record component
    /// that is never null is one fewer null check in an event handler.
    MenuSignals NONE = new MenuSignals() {

        @Override
        public String toString() {
            return "MenuSignals.NONE";
        }
    };

    /// The pointer has arrived on this row.
    ///
    /// **Every** row sends it, not only the ones with children: a submenu closes
    /// when the pointer moves to a sibling, and most siblings have no submenu of
    /// their own.
    default void hovered() {}

    /// Open this row's submenu **now** — a keyboard `Right`, or `Enter` on a row
    /// that leads somewhere.
    ///
    /// Separate from [#hovered()] because the delay is: the hover-intent wait
    /// stops a submenu dropping out of a pointer travelling past three rows, and
    /// there is nothing to travel past when a key was pressed.
    default void open() {}

    /// Go back: close the menu this row is in, or — at the root of a menu bar's
    /// menu — move to the menu on the left.
    ///
    /// `Left`, and the opposite of the arrow that opened this menu. Which of the
    /// two it means is the *menu's* business and not the row's, which is the
    /// whole reason this is a signal rather than a behaviour.
    default void back() {}

    /// Go forward from a row that leads nowhere: on a menu bar, the menu on the
    /// right.
    ///
    /// `Right` on a row **without** a submenu. With one, [#open()] is what that
    /// key means, because opening the branch under the cursor beats leaving the
    /// menu it is in.
    default void forward() {}
}
