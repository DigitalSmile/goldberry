package dev.goldberry.widgets.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.input.key.Shortcut;
import dev.goldberry.render.desktop.menubar.AppMenuItem;
import dev.goldberry.widget.Widget;

/// A `menubar`'s headings as the platform's own menu bar describes them —
/// what [MenuBar] hands to `Host.applicationMenu` on macOS.
///
/// The `Trays` translation for a different platform surface: an [Item] with
/// children is a submenu, an [Item] without is a command with its accelerator
/// and tick, a [Separator] is a dividing line, and anything else — a widget a
/// platform menu has nowhere to draw — is left out and said so.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
public final class AppMenus {

    private static final Logger LOG = LoggerFactory.getLogger(AppMenus.class);

    private AppMenus() {}

    /// The rows of `widgets`, in the platform's vocabulary.
    ///
    /// Public because it is the whole of the translation, and worth testing
    /// with no Mac: what the native bar shows is decided here.
    public static List<AppMenuItem> rowsOf(List<Widget> widgets) {
        Objects.requireNonNull(widgets, "widgets");
        var rows = new ArrayList<AppMenuItem>(widgets.size());
        for (var widget : widgets) {
            switch (widget) {
                case Separator _ -> rows.add(AppMenuItem.separator());
                case Item item -> rows.add(rowOf(item));
                default ->
                    LOG.debug(
                            "the platform's menu bar holds items and separators, so the {} in this menubar"
                                    + " is not shown there",
                            widget.getClass().getSimpleName());
            }
        }
        return List.copyOf(rows);
    }

    private static AppMenuItem rowOf(Item item) {
        var row = item.hasSubmenu()
                ? AppMenuItem.submenu(item.label(), rowsOf(item.submenu()))
                : AppMenuItem.command(item.label(), shortcut(item), item.onPress());
        if (item.isCheckable()) {
            row = row.checked(item.isChecked());
        }
        return item.disabled() ? row.disabled() : row;
    }

    /// The row's accelerator, or null when it has none or names one this
    /// toolkit cannot parse — a typo the row is then chosen without.
    private static @Nullable Shortcut shortcut(Item item) {
        var text = item.accelerator();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Shortcut.of(text);
        } catch (IllegalArgumentException e) {
            LOG.warn(
                    "\"{}\" on the menu item \"{}\" is not a shortcut this toolkit can bind: {}",
                    text,
                    item.label(),
                    e.getMessage());
            return null;
        }
    }
}
