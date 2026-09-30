package io.github.digitalsmile.goldberry.widgets.shell.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessTray;
import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.render.tray.TrayItem;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A `menu` description, read as the rows a desktop shell will draw.
///
/// This is the whole of what the toolkit is responsible for in a tray: the
/// platform owns the pixels, so what can be wrong is the *translation* — a
/// checkable row that arrives as a command, a submenu that loses its children, a
/// disabled row that is offered anyway.
class TraysTest {

    private final List<String> chosen = new ArrayList<>();

    private Menu menu() {
        return new Menu(
                List.of(
                        new Item("Open", () -> chosen.add("Open")),
                        new Separator(),
                        new Item("Notifications", () -> chosen.add("Notifications")).checked(true),
                        new Item("Recent")
                                .submenu(
                                        new Item("report.pdf", () -> chosen.add("report.pdf")),
                                        new Item("notes.md", () -> chosen.add("notes.md")).disabled(true)),
                        new Item("Quit", () -> chosen.add("Quit")).accelerator("Ctrl+Q")),
                Attributes.NONE);
    }

    @Test
    @DisplayName("reads an item as a command and a separator as a separator")
    void theOrdinaryRows() {
        var rows = Trays.rowsOf(menu());

        assertEquals(5, rows.size());
        assertEquals(TrayItem.Kind.COMMAND, rows.get(0).kind());
        assertEquals("Open", rows.get(0).label());
        assertEquals(TrayItem.Kind.SEPARATOR, rows.get(1).kind());
    }

    @Test
    @DisplayName("reads a checkable item as a checkbox, with the tick it was described with")
    void checkableItemsBecomeCheckboxes() {
        var row = Trays.rowsOf(menu()).get(2);

        assertEquals(TrayItem.Kind.CHECKBOX, row.kind());
        assertTrue(row.checked());
    }

    @Test
    @DisplayName("reads a nested item as a submenu and keeps its rows, disabled ones included")
    void submenusKeepTheirRows() {
        var row = Trays.rowsOf(menu()).get(3);

        assertEquals(TrayItem.Kind.SUBMENU, row.kind());
        assertEquals(
                List.of("report.pdf", "notes.md"),
                row.children().stream().map(TrayItem::label).toList());
        assertFalse(row.children().get(1).enabled());
    }

    @Test
    @DisplayName("drops a widget a tray menu has no place for, rather than failing")
    void foreignWidgetsAreDropped() {
        // A `text` in a menu is legal markup -- `Menu.children` takes any widget
        // -- and there is nothing the shell could do with it. Skipping is the
        // same answer `Accelerators` gives a non-item, and the warning is what
        // keeps it from being silent.
        var rows = Trays.rowsOf(List.<Widget>of(new Item("Open", () -> {}), new Text("not a row"), new Separator()));

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("shows a tray through the host, and choosing a row runs the item's command")
    void showingAndChoosing() {
        var host = new TestHost();

        var tray = (HeadlessTray)
                Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();

        tray.choose("Open");
        tray.choose("Recent/report.pdf");

        assertEquals(List.of("Open", "report.pdf"), chosen);
        assertEquals("Goldberry", tray.tooltip().orElseThrow());
    }

    @Test
    @DisplayName("runs a checkbox's command whichever way the platform toggled it")
    void checkboxesRunTheirCommand() {
        var host = new TestHost();
        var tray = (HeadlessTray)
                Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();

        // Described as ticked, so the shell's first click unticks it -- and the
        // item's command runs either way, because an `Item`'s command takes no
        // argument and an application that keeps its own state flips it there.
        assertTrue(tray.isChecked("Notifications"));
        tray.choose("Notifications");
        assertFalse(tray.isChecked("Notifications"));
        tray.choose("Notifications");

        assertEquals(List.of("Notifications", "Notifications"), chosen);
    }

    @Test
    @DisplayName("asks for a frame when a row is chosen, since nothing else will")
    void choosingARowAsksForAFrame() {
        // The defect the showcase found: every row but Quit did nothing, because
        // a handler that writes to a model is noticed by the sweep at the top of
        // a frame and a tray row asks for no frame. Quit worked because closing
        // a window is a platform effect rather than a model change.
        var host = new TestHost();
        var tray = (HeadlessTray)
                Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();
        var before = host.repaints();

        tray.choose("Open");
        tray.choose("Recent/report.pdf");

        assertEquals(before + 2, host.repaints());
    }

    @Test
    @DisplayName("carries the icon and the tooltip into the spec the backend is given")
    void specCarriesWhatTheAuthorWrote() {
        var tray = TrayIcon.of("Goldberry", menu()).tooltip("Goldberry — idle");

        var spec = tray.spec();

        assertEquals("Goldberry — idle", spec.tooltip());
        assertEquals(5, spec.items().size());
        assertEquals(null, spec.icon(), "no icon was described, so the platform's default is asked for");
    }

    // -- A picture for each shade of shell (ADR-0501) -------------------------

    private static final PixelFormat PIXELS = PixelFormat.BGRA32_PREMULTIPLIED;

    /// Dark ink, for a light panel.
    private final PixelBuffer forLightShell = PixelBuffer.allocate(PhysicalSize.of(32, 32), PIXELS);

    /// A light mark, for a dark panel. A different size as well as a different
    /// buffer, so that even `equals` could not mistake one for the other.
    private final PixelBuffer forDarkShell = PixelBuffer.allocate(PhysicalSize.of(64, 64), PIXELS);

    private static HeadlessTray onlyTray(TestHost host) {
        var trays = host.trays();
        assertEquals(1, trays.size(), "one tray was shown");
        return trays.getFirst();
    }

    @Test
    @DisplayName("starts a pair of icons on the one for what the desktop says now")
    void aPairStartsOnTheDesktopsSetting() {
        var host = new TestHost();
        host.systemTheme(SystemTheme.DARK);

        Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell));

        assertSame(forDarkShell, onlyTray(host).icon().orElseThrow());
    }

    @Test
    @DisplayName("shows the light-shell icon where the desktop says nothing, as CSS reads no preference")
    void aSilentDesktopGetsTheLightShellIcon() {
        var host = new TestHost();

        Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell));

        assertSame(forLightShell, onlyTray(host).icon().orElseThrow());
    }

    @Test
    @DisplayName("swaps the icon in place when the desktop's theme changes, and keeps the menu")
    void aThemeChangeSwapsTheIcon() {
        var host = new TestHost();
        host.systemTheme(SystemTheme.LIGHT);
        Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell));
        var shown = onlyTray(host);
        var rows = shown.items();

        host.systemTheme(SystemTheme.DARK);
        assertSame(forDarkShell, shown.icon().orElseThrow(), "dusk arrived and the icon stayed dark ink");

        host.systemTheme(SystemTheme.LIGHT);
        assertSame(forLightShell, shown.icon().orElseThrow());

        assertSame(shown, onlyTray(host), "a swap is not a rebuild: the same tray is up");
        assertSame(rows, shown.items());
    }

    @Test
    @DisplayName("stops listening when the tray is closed, so a rebuilt tray leaves nothing behind")
    void closingStopsListening() {
        var host = new TestHost();
        var before = host.systemThemeListenerCount();

        var tray = Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell))
                .orElseThrow();
        assertEquals(before + 1, host.systemThemeListenerCount());

        tray.close();
        tray.close();

        assertTrue(tray.isClosed());
        assertEquals(before, host.systemThemeListenerCount(), "the closed tray is still listening");
        // A closed headless tray refuses a new icon, so a listener left behind
        // would throw here rather than pass quietly.
        host.systemTheme(SystemTheme.DARK);
    }

    @Test
    @DisplayName("rebuilding a tray to change its menu keeps one listener, not one per rebuild")
    void rebuildsDoNotAccumulateListeners() {
        var host = new TestHost();
        var before = host.systemThemeListenerCount();
        var description = TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell);

        var tray = Trays.show(host, description).orElseThrow();
        for (var i = 0; i < 5; i++) {
            tray.close();
            tray = Trays.show(host, description.tooltip("Goldberry — " + i)).orElseThrow();
        }

        assertEquals(before + 1, host.systemThemeListenerCount());
        host.systemTheme(SystemTheme.DARK);
        assertSame(forDarkShell, onlyTray(host).icon().orElseThrow());
    }

    @Test
    @DisplayName("stops following once the application sets an icon of its own")
    void anExplicitIconTakesThePictureOver() {
        var host = new TestHost();
        var badge = PixelBuffer.allocate(PhysicalSize.of(48, 48), PIXELS);
        var tray = Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell))
                .orElseThrow();

        tray.icon(badge);
        host.systemTheme(SystemTheme.DARK);

        assertSame(badge, onlyTray(host).icon().orElseThrow(), "a swap at dusk undid the application's badge");
        assertEquals(0, host.systemThemeListenerCount());
    }

    @Test
    @DisplayName("lets go of a tray that was taken down underneath its handle")
    void aTrayClosedUnderneathStopsTheListening() {
        var host = new TestHost();
        Trays.show(host, TrayIcon.of("Goldberry", menu()).icons(forLightShell, forDarkShell));

        // What a backend does to every tray it still has when it shuts down.
        onlyTray(host).close();
        host.systemTheme(SystemTheme.DARK);

        assertEquals(0, host.systemThemeListenerCount());
    }

    @Test
    @DisplayName("leaves a single-icon tray alone: shown as described, and listening to nothing")
    void aSingleIconIsUnchanged() {
        var host = new TestHost();
        host.systemTheme(SystemTheme.LIGHT);

        var tray = Trays.show(host, TrayIcon.of("Goldberry", menu()).icon(forLightShell))
                .orElseThrow();
        host.systemTheme(SystemTheme.DARK);

        assertSame(onlyTray(host), tray, "a tray with one picture needs no handle of its own");
        assertSame(forLightShell, onlyTray(host).icon().orElseThrow());
        assertEquals(0, host.systemThemeListenerCount());
    }
}
