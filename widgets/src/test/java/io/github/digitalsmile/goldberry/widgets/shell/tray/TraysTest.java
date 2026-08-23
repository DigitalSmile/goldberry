package io.github.digitalsmile.goldberry.widgets.shell.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessTray;
import io.github.digitalsmile.goldberry.render.tray.TrayItem;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A `menu` description, read as the rows a desktop shell will draw.
///
/// This is the whole of what the toolkit is responsible for in a tray: the
/// platform owns the pixels, so what can be wrong is the *translation* — a
/// checkable row that arrives as a command, a submenu that loses its children, a
/// disabled row that is offered anyway.
class TraysTest {

    private final List<String> chosen = new ArrayList<>();

    private Menu menu() {
        return new Menu(List.of(
                new Item("Open", () -> chosen.add("Open")),
                new Separator(),
                new Item("Notifications", () -> chosen.add("Notifications")).checked(true),
                new Item("Recent").submenu(
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
        assertEquals(List.of("report.pdf", "notes.md"),
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
        var rows = Trays.rowsOf(List.<Widget>of(
                new Item("Open", () -> {
                }),
                new Text("not a row"),
                new Separator()));

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("shows a tray through the host, and choosing a row runs the item's command")
    void showingAndChoosing() {
        var host = new TestHost();

        var tray = (HeadlessTray) Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();

        tray.choose("Open");
        tray.choose("Recent/report.pdf");

        assertEquals(List.of("Open", "report.pdf"), chosen);
        assertEquals("Goldberry", tray.tooltip().orElseThrow());
    }

    @Test
    @DisplayName("runs a checkbox's command whichever way the platform toggled it")
    void checkboxesRunTheirCommand() {
        var host = new TestHost();
        var tray = (HeadlessTray) Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();

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
        var tray = (HeadlessTray) Trays.show(host, TrayIcon.of("Goldberry", menu())).orElseThrow();
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
}
