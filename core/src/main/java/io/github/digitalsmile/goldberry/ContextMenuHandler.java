package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.LogicalRect;

/// What to do when a widget that named a context menu is right-clicked —
/// [Host#onContextMenu].
///
/// ## Why this is a callback and not a registry of menus
///
/// `docs/core-widgets.md` §8 gives any widget `context-menu="menuId"`, and the
/// launcher is the only thing that can notice the right-click: it has the router,
/// which knows what is under the pointer, and the window, which is where a popup
/// goes. What it cannot do is **build** the menu — a menu is a widget in the
/// catalog and the launcher is `:core`'s, which ships none
/// ([ADR-0092](../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)) —
/// and it cannot open one either, because opening a menu means wrapping every
/// item so that choosing it closes the stack, which is `Menus`'
/// ([ADR-0106](../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)).
///
/// So the seam is exactly one sentence wide: **`:core` says which name and
/// where; `:widgets` says what that name is and opens it**
/// ([ADR-0108](../../../../../book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)).
@FunctionalInterface
public interface ContextMenuHandler {

    /// A widget naming `menuId` was right-clicked.
    ///
    /// @param menuId the name the widget carried, resolved by whoever registered
    ///               this — unknown names are the handler's to refuse, exactly as
    ///               an unknown `press=` is a registry's
    /// @param at     where the click landed, in the window's logical coordinates,
    ///               as a rectangle of no size: a context menu is anchored to the
    ///               pointer rather than to the widget, so two right-clicks in one
    ///               list open two menus in two places
    void open(String menuId, LogicalRect at);
}
