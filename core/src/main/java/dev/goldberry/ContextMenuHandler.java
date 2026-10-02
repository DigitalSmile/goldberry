package dev.goldberry;

import dev.goldberry.render.model.LogicalRect;

/// What to do when a widget that named a context menu is right-clicked: the
/// handler [Host#onContextMenu] takes.
///
/// An application using the catalogue rarely writes one. `Menus.contextMenus`
/// registers a handler that turns each name into a menu and opens it:
///
/// ```java
/// Menus.contextMenus(host, Map.of("rows", rowMenu()));
/// ```
///
/// `context-menu="menuId"` on any widget names a menu, and the launcher is the
/// only thing that can notice the right-click: it has the router, which knows
/// what is under the pointer, and the window, which is where a popup goes. What
/// it cannot do is **build** the menu, because a menu is a widget in the
/// catalogue and `:core` ships no widgets, and it cannot open one either,
/// because opening a menu means wrapping every item so that choosing it closes
/// the stack, which is `Menus`' job. So the seam is exactly one sentence wide:
/// **`:core` says which name and where; `:widgets` says what that name is and
/// opens it**.
///
/// Read more: [Context menus](https://goldberry.dev/docs/guide/input.html#context-menus).
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
