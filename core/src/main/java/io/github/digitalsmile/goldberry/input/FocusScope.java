package io.github.digitalsmile.goldberry.input;

/// Which arrow keys rove inside a composite — `docs/design-system.md` §7.2.
///
/// A composite is **one Tab stop** with the arrow keys moving focus between its
/// items ([ADR-0073](../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
/// This says which pair of arrows does that, and therefore which pair is left for
/// the widget to mean something else by.
///
/// ## Why the axis is not always "both"
///
/// A radio group genuinely has no axis: its direction is the stylesheet's —
/// `flex-direction` on `radio-group`, which `.inline` flips — so input cannot
/// know which pair a user is looking at, and answering only one would be wrong
/// half the time. That is [#BOTH], and it is ARIA's rule for a radio group.
///
/// Every other composite in `docs/core-widgets.md` **does** have one, and the
/// difference only shows on the path where the widget *declines* the key. A
/// vertical menu's `Right` opens a submenu; on an item that has none, the item
/// declines it — and a [#BOTH] scope would then quietly move focus to the next
/// item, which is not what the user asked for and is exactly the kind of wrong
/// that reads as a toolkit bug. [#VERTICAL] makes it do nothing, which is right.
///
/// The same argument covers a menu bar (`Down` opens a menu rather than moving
/// along the bar) and a tab list (`Down` moves into the panel rather than to the
/// next tab).
///
/// `Home` and `End` reach the ends of **any** scope regardless of axis, because
/// they name a position in the set rather than a direction on screen.
public enum FocusScope {

    /// Not a composite. Every focusable descendant is its own Tab stop, which is
    /// the ordinary case and the default.
    NONE,

    /// `Left` and `Right` rove; `Up` and `Down` are the widget's.
    ///
    /// A menu bar, a tab list, a toolbar.
    HORIZONTAL,

    /// `Up` and `Down` rove; `Left` and `Right` are the widget's.
    ///
    /// A dropdown menu, a list box.
    VERTICAL,

    /// Both pairs rove.
    ///
    /// For a composite whose direction is a stylesheet's decision rather than the
    /// widget's — `radio-group`, which is a column by default and a row with
    /// `.inline`.
    BOTH;

    /// Whether this scope answers to arrows along `axis`.
    ///
    /// @param axis the axis the pressed arrow lies on, or null for `Home`/`End`,
    ///             which belong to no axis and reach the ends of any scope
    public boolean roves(Axis axis) {
        if (this == NONE) {
            return false;
        }
        return axis == null || this == BOTH || switch (axis) {
            case HORIZONTAL -> this == FocusScope.HORIZONTAL;
            case VERTICAL -> this == FocusScope.VERTICAL;
        };
    }

    /// The axis an arrow key lies on.
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }
}
