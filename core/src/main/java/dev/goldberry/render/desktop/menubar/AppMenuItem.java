package dev.goldberry.render.desktop.menubar;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.key.Shortcut;

/// One row of the application's menu bar, as the platform draws it.
///
/// ```java
/// var file = AppMenuItem.submenu("File", List.of(
///         AppMenuItem.command("Open…", Shortcut.of("Ctrl+O"), this::open),
///         AppMenuItem.separator(),
///         AppMenuItem.command("Close", Shortcut.of("Ctrl+W"), this::close)));
/// host.applicationMenu(List.of(file));
/// ```
///
/// **Not a widget**, for [dev.goldberry.render.tray.TrayItem]'s reason: the
/// platform draws these rows in its own font and place, so there is nothing to
/// style and no pointer to route. A `menubar` widget is translated into these
/// where the platform has a menu bar of its own.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
///
/// @param kind     what the row is
/// @param label    its text; ignored for [Kind#SEPARATOR]
/// @param enabled  false greys it out
/// @param checked  true or false for a row that shows a tick, null for one
///                 that does not
/// @param shortcut the accelerator, or null. On macOS it is shown and fired by
///                 the menu itself, with `Ctrl` read as `Cmd`
/// @param onChosen what runs, on the UI thread, when the row is chosen, or null
/// @param children a submenu's rows
public record AppMenuItem(
        Kind kind,
        @Nullable String label,
        boolean enabled,
        @Nullable Boolean checked,
        @Nullable Shortcut shortcut,
        @Nullable Runnable onChosen,
        List<AppMenuItem> children) {

    /// What a row is.
    public enum Kind {
        /// A row that does something.
        COMMAND,
        /// A row that opens [AppMenuItem#children].
        SUBMENU,
        /// A dividing line.
        SEPARATOR
    }

    /// Written out so that the parameters taking null can say so.
    public AppMenuItem(
            Kind kind,
            @Nullable String label,
            boolean enabled,
            @Nullable Boolean checked,
            @Nullable Shortcut shortcut,
            @Nullable Runnable onChosen,
            @Nullable List<AppMenuItem> children) {
        Objects.requireNonNull(kind, "kind");
        children = List.copyOf(children == null ? List.of() : children);
        if (kind != Kind.SEPARATOR) {
            Objects.requireNonNull(label, "label");
        }
        if (kind != Kind.SUBMENU && !children.isEmpty()) {
            throw new IllegalArgumentException(
                    "only a submenu has children, and " + kind + " " + label + " has " + children.size());
        }
        this.kind = kind;
        this.label = label;
        this.enabled = enabled;
        this.checked = checked;
        this.shortcut = shortcut;
        this.onChosen = onChosen;
        this.children = children;
    }

    /// A command.
    public static AppMenuItem command(String label, @Nullable Shortcut shortcut, @Nullable Runnable onChosen) {
        return new AppMenuItem(Kind.COMMAND, label, true, null, shortcut, onChosen, List.of());
    }

    /// A row that opens `children`.
    public static AppMenuItem submenu(String label, List<AppMenuItem> children) {
        return new AppMenuItem(Kind.SUBMENU, label, true, null, null, null, children);
    }

    /// A dividing line.
    public static AppMenuItem separator() {
        return new AppMenuItem(Kind.SEPARATOR, null, true, null, null, null, List.of());
    }

    /// The same row, greyed out.
    public AppMenuItem disabled() {
        return new AppMenuItem(kind, label, false, checked, shortcut, onChosen, children);
    }

    /// The same row, showing a tick or not.
    public AppMenuItem checked(boolean value) {
        return new AppMenuItem(kind, label, enabled, value, shortcut, onChosen, children);
    }

    /// This row and every row under it, running `after` once a row's own
    /// action has: how a host gets a frame after a menu command, which arrives
    /// from the platform with no event behind it.
    public AppMenuItem andThen(Runnable after) {
        Objects.requireNonNull(after, "after");
        var action = onChosen;
        Runnable wrapped = action == null
                ? null
                : () -> {
                    try {
                        action.run();
                    } finally {
                        after.run();
                    }
                };
        return new AppMenuItem(
                kind,
                label,
                enabled,
                checked,
                shortcut,
                wrapped,
                children.stream().map(child -> child.andThen(after)).toList());
    }
}
