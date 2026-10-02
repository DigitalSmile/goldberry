package dev.goldberry.widgets.shell.tray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.Host;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TrayItem;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Menu;
import dev.goldberry.widgets.menu.Separator;

/// Puts a [TrayIcon] on the desktop, and turns a [Menu] into the rows the shell
/// will draw.
///
/// ```java
/// Optional<BackendTray> tray = Trays.show(host, TrayIcon.of("Goldberry", menu));
/// tray.ifPresent(BackendTray::close);   // in stop(): the tray outlives the process otherwise
/// ```
///
/// A `Menu` is a value and showing one is a separate call, the same split the
/// `menu` package makes. The same value works here because what is short-lived
/// about a menu is the popup, not the description, and a tray menu, which the
/// platform holds for as long as the icon is up, is the longest-lived opening
/// there is. The menu cannot be changed in place: close the tray and show a
/// new one.
///
/// A tray row is a GTK menu item, an `NSMenuItem` or a Win32 popup entry. It
/// has a label, an enabled state and a tick, and that is the whole vocabulary.
/// So three things an author may reasonably have written on an [Item] are
/// **dropped here, with a warning**, rather than silently:
///
///   - an **icon**, which no platform's tray API takes;
///   - an **accelerator**, which is a key bound to a window and a tray has no
///     window — the same command in a `menubar` still registers one;
///   - any widget that is neither an `item` nor a `separator`, because a tray
///     menu has no place to put one.
///
/// The warning is the point. A tray that quietly ignored half a description
/// would be a menu an author kept editing without effect.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
public final class Trays {

    private static final Logger LOG = LoggerFactory.getLogger(Trays.class);

    private Trays() {}

    /// Shows `tray` on the desktop, if there is one.
    ///
    /// A tray described with [TrayIcon#icons] starts on the picture for what the
    /// desktop says now and **follows the setting** from then on: each change
    /// swaps the icon in place, through [BackendTray#icon], and leaves the menu
    /// alone. The handle that comes back owns that listening — closing it stops
    /// it, and so does handing it an icon of the caller's own, which is the
    /// caller taking the picture over. A tray with one picture, or none, is shown
    /// exactly as described and listens to nothing.
    ///
    /// @return the handle that takes it down, or empty when this session has no
    ///         notification area — see [Host#tray]
    public static Optional<BackendTray> show(Host host, TrayIcon tray) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(tray, "tray");
        var shown = host.tray(tray.spec(host.systemTheme()));
        if (tray.picture() instanceof TrayIcon.ThemePair pair) {
            return shown.map(backend -> ThemedTray.follow(host, backend, pair));
        }
        return shown;
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
                case Separator _ -> rows.add(TrayItem.separator());
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

    private static void run(@Nullable Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
