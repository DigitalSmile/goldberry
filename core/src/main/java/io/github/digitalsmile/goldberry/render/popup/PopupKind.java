package io.github.digitalsmile.goldberry.render.popup;

/// What kind of popup a [PopupSpec] asks for.
///
/// Not a style: **every window manager treats the two differently**, and SDL
/// refuses a popup that claims to be both. A menu may take the keyboard; a
/// tooltip must never, or the caret leaves the field the tooltip is describing.
/// Platforms also differ in what they will do to each — animate it, shadow it,
/// keep it out of the window list, dismiss it when the pointer leaves.
public enum PopupKind {

    /// A menu, a dropdown, a `select`'s list, a `popover` — anything the user is
    /// meant to act on. May take input focus.
    MENU,

    /// A panel that hangs off something the user is **still using** — §4's
    /// suggestion list, and a combobox's dropdown.
    ///
    /// A menu window in every respect the window manager cares about — it takes
    /// the pointer, it is placed and shadowed like a menu, it is in no window
    /// list — and **not focusable**, so the keyboard stays on the field it hangs
    /// off ([ADR-0187](../../../../../../book/src/adr/0187-a-panel-takes-the-pointer-and-leaves-the-keyboard.md)).
    ///
    /// It is not [#TOOLTIP], and the difference is the whole reason it exists: a
    /// tooltip is "never interacted with", so platforms give it no input at all.
    /// A suggestion list that borrowed the tooltip flag stopped stealing the
    /// keyboard and stopped being clickable in the same change.
    ATTACHED,

    /// A tooltip: shown, read and never interacted with. Never takes focus.
    ///
    /// `docs/core-widgets.md` §7 says a tooltip is "never focusable itself" and
    /// shows "on hover *and on keyboard focus*" — which only works if showing it
    /// does not move the focus that summoned it.
    TOOLTIP
}
