package dev.goldberry.widgets.shell.tray;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.Host;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TrayItem;
import dev.goldberry.widgets.menu.Accelerators;
import dev.goldberry.widgets.menu.Menu;

/// A [TrayIcon] on the desktop: what [Trays#show] answers.
///
/// ```java
/// tray.menu(new Menu(new Item("Notifications", actions::toggle).checked(model.notifying())));
/// ```
///
/// **[#menu] gives the menu new rows while the icon is up.** A menu of the same
/// shape is changed in place, a label, a tick, whether a row is offered, and a
/// menu of another shape replaces the rows under the same icon. Nothing
/// flickers in the notification area either way.
///
/// **The menu is the truth.** The shell ticks a checkbox before its command
/// runs, and a command takes no argument, so a command that declines the click
/// describes the menu again with the tick as it was. One that accepts describes
/// it with the new tick, or leaves it as the shell made it.
///
/// **An item's accelerator is bound on the window that showed the tray**, as a
/// `menubar` binds its rows', and given back when the menu changes or the tray
/// closes. No platform draws the key beside a tray row, so the binding works
/// and is not shown.
///
/// UI-thread confined, like the tray it holds. Close it in `stop()`: a tray
/// left up outlives the process on some desktops.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
public final class ShownTray implements BackendTray {

    private final Host host;
    private final BackendTray tray;

    /// The accelerators the current menu bound, which [#close] and the next
    /// [#menu] give back.
    private Set<Shortcut> bound;

    private ShownTray(Host host, BackendTray tray, Set<Shortcut> bound) {
        this.host = host;
        this.tray = tray;
        this.bound = bound;
    }

    /// Holds `tray`, just shown with `menu`, and binds the menu's accelerators on
    /// `host`'s window.
    static ShownTray of(Host host, BackendTray tray, Menu menu) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(tray, "tray");
        Objects.requireNonNull(menu, "menu");
        var shown = new ShownTray(host, tray, Set.of());
        shown.bound = Accelerators.bind(host, menu.children(), shown);
        return shown;
    }

    /// Gives the tray `menu`'s rows, keeping the icon where it is, and its
    /// accelerators in place of the last menu's.
    ///
    /// Called from inside a row's command, the rows change when the command
    /// returns.
    public void menu(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        tray.rows(Trays.rowsOf(menu));
        Accelerators.unbind(host, bound, this);
        bound = Accelerators.bind(host, menu.children(), this);
    }

    /// The same for rows already in the platform's vocabulary. Binds no
    /// accelerators, since a [TrayItem] carries none.
    @Override
    public void rows(List<TrayItem> rows) {
        tray.rows(rows);
    }

    @Override
    public void icon(PixelBuffer icon) {
        tray.icon(icon);
    }

    @Override
    public void tooltip(String tooltip) {
        tray.tooltip(tooltip);
    }

    @Override
    public boolean isClosed() {
        return tray.isClosed();
    }

    /// Takes the icon down and gives the accelerators back. Idempotent.
    @Override
    public void close() {
        if (!bound.isEmpty()) {
            Accelerators.unbind(host, bound, this);
            bound = Set.of();
        }
        tray.close();
    }

    @Override
    public String toString() {
        return "ShownTray[" + tray + ", " + bound.size() + " accelerators]";
    }
}
