package io.github.digitalsmile.goldberry.widgets.shell.tray;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.tray.TraySpec;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;

/// An icon this application asks the desktop to show — `docs/core-widgets.md`
/// §9's `tray-icon`.
///
/// **A value, not a widget**, and the second one in the catalog after `toast`
/// (ADR-0177).
/// The reason is stronger here than it was there: a toast is at least drawn by
/// Goldberry, and this is not drawn by Goldberry at all. The shell owns the
/// pixels, the font, the spacing and the click. There is no box to lay out, no
/// `ComputedStyle` to compute and no pointer event to route — so the parity
/// invariant's third clause, *CSS-styleable*, has nothing to attach to, and
/// pretending otherwise would put a widget in the catalog that no stylesheet
/// could ever affect.
///
/// What it does share with the catalog is its **menu**: the same [Menu] of
/// `item`s and `separator`s a `menubar` holds, walked into the platform's
/// vocabulary by [Trays]. An author writes one description and can show it in a
/// window, in a context menu or in the tray.
///
/// ```java
/// var tray = Trays.show(host, TrayIcon.of("Goldberry", new Menu(List.of(
///         new Item("Open", app::open),
///         new Separator(),
///         new Item("Quit", app::quit)))).icon(darkModeIcon));
/// // ...
/// tray.ifPresent(BackendTray::close);
/// ```
///
/// @param icon    the icon's pixels, or null for whatever the desktop shows for
///                an application that supplied none. **Physical pixels**: a tray
///                icon is not on a Goldberry window and inherits no scale
/// @param tooltip the hover text, or null — not every platform shows one
/// @param menu    the rows the shell draws when the icon is clicked
public record TrayIcon(PixelBuffer icon, String tooltip, Menu menu) {

    public TrayIcon {
        Objects.requireNonNull(menu, "menu");
    }

    /// A tray with a tooltip and a menu, and the platform's own icon.
    public static TrayIcon of(String tooltip, Menu menu) {
        return new TrayIcon(null, tooltip, menu);
    }

    /// The same tray with a different picture — what a theme switch builds, since
    /// the icon sits on the *desktop's* background and the cascade does not
    /// reach it.
    public TrayIcon icon(PixelBuffer value) {
        return new TrayIcon(value, tooltip, menu);
    }

    /// The same tray with different hover text.
    public TrayIcon tooltip(String value) {
        return new TrayIcon(icon, value, menu);
    }

    /// This tray as the backend SPI's description of one.
    public TraySpec spec() {
        return new TraySpec(icon, tooltip, Trays.rowsOf(menu));
    }
}
