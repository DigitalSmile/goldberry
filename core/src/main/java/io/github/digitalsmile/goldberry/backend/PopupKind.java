package io.github.digitalsmile.goldberry.backend;

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

    /// A tooltip: shown, read and never interacted with. Never takes focus.
    ///
    /// `docs/core-widgets.md` §7 says a tooltip is "never focusable itself" and
    /// shows "on hover *and on keyboard focus*" — which only works if showing it
    /// does not move the focus that summoned it.
    TOOLTIP
}
