package dev.goldberry.widgets.menu;

import java.util.Objects;
import java.util.function.Consumer;

import dev.goldberry.Popup;

/// A menu on screen whose rows can change while it is open, from
/// [Menus#show].
///
/// What a menu needs when one of its rows follows something outside the menu:
/// a text field's Paste is disabled while the clipboard is empty, and enables
/// itself the moment another application copies, without the menu closing.
///
/// ```java
/// var shown = Menus.show(host, at, menu());
/// shown.ifPresent(menu -> clipboard.onChange(() -> menu.update(menu())));
/// ```
///
/// The rows are reconciled in place, so the keyboard keeps its row and nothing
/// flickers. An update after the menu closed does nothing.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menu).
public final class ShownMenu {

    private final Popup popup;
    private final Consumer<Menu> replace;

    ShownMenu(Popup popup, Consumer<Menu> replace) {
        this.popup = Objects.requireNonNull(popup, "popup");
        this.replace = Objects.requireNonNull(replace, "replace");
    }

    /// Shows `menu`'s rows in place of the ones on screen. Does nothing once the
    /// menu has closed.
    public void update(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        replace.accept(menu);
    }

    /// Whether it is still on screen.
    public boolean isOpen() {
        return popup.isOpen();
    }

    /// Closes it, and anything it had open. Idempotent.
    public void close() {
        popup.close();
    }

    /// The popup it is in.
    public Popup popup() {
        return popup;
    }

    @Override
    public String toString() {
        return "ShownMenu[" + (isOpen() ? "open" : "closed") + "]";
    }
}
