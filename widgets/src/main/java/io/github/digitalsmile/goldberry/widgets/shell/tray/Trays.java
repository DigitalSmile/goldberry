package io.github.digitalsmile.goldberry.widgets.shell.tray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.tray.BackendTray;
import io.github.digitalsmile.goldberry.render.tray.TrayItem;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;

/// Puts a [TrayIcon] on the desktop, and turns a [Menu] into the rows the shell
/// will draw.
///
/// The split `menu` already has: a `Menu` is a value and **showing** one is not
/// ([ADR-0106](../../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)).
/// The reason the *same* value works here is
/// [ADR-0163](../../../../../../../../book/src/adr/0163-a-menu-bar-owns-its-menus.md)'s:
/// what is short-lived about a menu is the popup, not the description, so a
/// description outlives an opening — and a tray menu, which the platform holds
/// for as long as the icon is up, is the longest-lived opening there is.
/// [io.github.digitalsmile.goldberry.widgets.menu.Accelerators] walks the same
/// value for the same reason.
///
/// ## What the platform cannot draw
///
/// A tray row is a GTK menu item, an `NSMenuItem` or a Win32 popup entry. It has
/// a label, an enabled state and a tick, and that is the whole vocabulary. So
/// three things an author may reasonably have written on an [Item] are **dropped
/// here, with a warning**, rather than silently:
///
///   - an **icon**, which no platform's tray API takes;
///   - an **accelerator**, which is a key bound to a window and a tray has no
///     window — the same command in a `menubar` still registers one;
///   - any widget that is neither an `item` nor a `separator`, because a tray
///     menu has no place to put one.
///
/// The warning is the point. A tray that quietly ignored half a description
/// would be a menu an author kept editing without effect.
public final class Trays {

    private static final Logger LOG = LoggerFactory.getLogger(Trays.class);

    private Trays() {}

    /// Shows `tray` on the desktop, if there is one.
    ///
    /// @return the handle that takes it down, or empty when this session has no
    ///         notification area — see [Host#tray]
    public static Optional<BackendTray> show(Host host, TrayIcon tray) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(tray, "tray");
        return host.tray(tray.spec());
    }

    /// The rows of `menu`, as the platform's vocabulary.
    ///
    /// Public because it is the whole of the translation and is worth testing
    /// without a desktop: what a tray menu *is* is decided here, and the two
    /// backends only carry it.
    public static List<TrayItem> rowsOf(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        return rowsOf(menu.children());
    }

    /// [#rowsOf(Menu)] for a bare list of widgets — a submenu's children.
    public static List<TrayItem> rowsOf(List<Widget> widgets) {
        Objects.requireNonNull(widgets, "widgets");
        var rows = new ArrayList<TrayItem>(widgets.size());
        for (var widget : widgets) {
            switch (widget) {
                case Separator ignored -> rows.add(TrayItem.separator());
                case Item item -> rows.add(rowOf(item));
                default ->
                    LOG.warn(
                            "a tray menu holds items and separators, so the {} in it is not shown —"
                                    + " the platform draws these rows and has nowhere to put one",
                            widget.getClass().getSimpleName());
            }
        }
        return List.copyOf(rows);
    }

    private static TrayItem rowOf(Item item) {
        if (item.icon() != null) {
            LOG.warn("the tray row \"{}\" has an icon, which no platform's tray menu draws", item.label());
        }
        if (item.accelerator() != null && !item.accelerator().isBlank()) {
            LOG.warn(
                    "the tray row \"{}\" names the accelerator {}, which is a key bound to a"
                            + " window and a tray has none; it is not registered here",
                    item.label(),
                    item.accelerator());
        }
        var row = row(item);
        return item.disabled() ? row.disabled() : row;
    }

    private static TrayItem row(Item item) {
        if (item.hasSubmenu()) {
            // A submenu's own command is unreachable in a tray for the reason it
            // is unreachable in a menu: choosing the row opens the children.
            return TrayItem.submenu(item.label(), rowsOf(item.submenu()));
        }
        var press = item.onPress();
        if (item.isCheckable()) {
            // The tick the platform shows is **its own** after the click: SDL
            // toggles a checkbox before it calls back, and nothing here can
            // refuse that. An application whose handler declines has to rebuild
            // the tray to say so, which is why `Menu` is a value it re-describes
            // rather than a thing it mutates.
            return TrayItem.checkbox(item.label(), item.isChecked(), checked -> run(press));
        }
        return TrayItem.command(item.label(), checked -> run(press));
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
