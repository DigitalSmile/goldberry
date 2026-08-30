package io.github.digitalsmile.goldberry.render.backend.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.render.tray.TrayItem;
import io.github.digitalsmile.goldberry.render.tray.TraySpec;

/// The tray half of the backend SPI, where a tray menu can be observed at all.
///
/// A real tray menu is drawn by the desktop's shell: there is no golden image of
/// a GTK popup and nothing to hit-test. So every rule about *choosing* a row —
/// that a checkbox toggles before its handler runs, that a disabled row is never
/// offered, that a submenu is reached by path — is asserted here, against the
/// headless backend, and the SDL backend's job is to be the same translation
/// twice over.
class HeadlessTrayTest {

    private HeadlessBackend backend;
    private final List<String> chosen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        chosen.clear();
    }

    @AfterEach
    void tearDown() {
        backend.close();
    }

    private HeadlessTray tray() {
        return (HeadlessTray) backend.createTray(TraySpec.of(
                        "Goldberry",
                        List.of(
                                TrayItem.command("Open", checked -> chosen.add("Open")),
                                TrayItem.separator(),
                                TrayItem.checkbox(
                                        "Notifications", true, checked -> chosen.add("Notifications=" + checked)),
                                TrayItem.checkbox("Sounds", false, checked -> chosen.add("Sounds=" + checked)),
                                TrayItem.submenu(
                                        "Recent",
                                        List.of(
                                                TrayItem.command("report.pdf", checked -> chosen.add("report.pdf")),
                                                TrayItem.command("notes.md", checked -> chosen.add("notes.md"))
                                                        .disabled())),
                                TrayItem.command("Quit", checked -> chosen.add("Quit")))))
                .orElseThrow();
    }

    @Test
    @DisplayName("is never empty on a backend with no platform to lack one")
    void headlessAlwaysHasATray() {
        var tray = tray();

        assertFalse(tray.isClosed());
        assertEquals(List.of(tray), backend.trays());
        assertEquals("Goldberry", tray.tooltip().orElseThrow());
        assertTrue(tray.icon().isEmpty(), "no icon was described, so none is set");
    }

    @Test
    @DisplayName("runs a command's handler when the row is chosen")
    void choosingACommandRunsIt() {
        var tray = tray();

        tray.choose("Open");
        tray.choose("Quit");

        assertEquals(List.of("Open", "Quit"), chosen);
    }

    @Test
    @DisplayName("toggles a checkbox before its handler runs, and tells it the new state")
    void aCheckboxTogglesFirst() {
        var tray = tray();

        // Described as checked, so the first click turns it off -- and the
        // handler is told `false`, which is the state after the click and not
        // the one it was described with. Doing this the other way round is the
        // bug this test exists for: every platform applies the toggle itself.
        assertTrue(tray.isChecked("Notifications"));
        tray.choose("Notifications");
        assertFalse(tray.isChecked("Notifications"));

        tray.choose("Sounds");
        assertTrue(tray.isChecked("Sounds"));

        tray.choose("Notifications");

        assertEquals(List.of("Notifications=false", "Sounds=true", "Notifications=true"), chosen);
    }

    @Test
    @DisplayName("reaches a submenu's row by its path")
    void submenuRowsAreReachedByPath() {
        var tray = tray();

        tray.choose("Recent/report.pdf");

        assertEquals(List.of("report.pdf"), chosen);
    }

    @Test
    @DisplayName("refuses a disabled row, because the shell would never offer it")
    void disabledRowsAreNotChoosable() {
        var tray = tray();

        var refused = assertThrows(IllegalStateException.class, () -> tray.choose("Recent/notes.md"));

        assertTrue(refused.getMessage().contains("disabled"));
        assertEquals(List.of(), chosen);
    }

    @Test
    @DisplayName("names the rows it does have when asked for one it does not")
    void unknownPathsSayWhatThereIs() {
        var tray = tray();

        var refused = assertThrows(IllegalArgumentException.class, () -> tray.choose("Preferences"));

        assertTrue(
                refused.getMessage().contains("Recent/report.pdf"),
                "the failure should list the paths that exist: " + refused.getMessage());
        // A separator has no path, so it cannot be named and cannot be chosen.
        assertThrows(IllegalArgumentException.class, () -> tray.choose(""));
    }

    @Test
    @DisplayName("takes a new icon and a new tooltip without being rebuilt")
    void iconAndTooltipAreReplaceable() {
        var tray = tray();
        var icon = PixelBuffer.allocate(PhysicalSize.of(32, 32), PixelFormat.BGRA32_PREMULTIPLIED);

        tray.icon(icon);
        tray.tooltip("Goldberry — 3 unread");

        assertEquals(icon, tray.icon().orElseThrow());
        assertEquals("Goldberry — 3 unread", tray.tooltip().orElseThrow());
    }

    @Test
    @DisplayName("closes once, and the backend forgets it")
    void closingIsIdempotent() {
        var tray = tray();

        tray.close();
        tray.close();

        assertTrue(tray.isClosed());
        assertEquals(List.of(), backend.trays());
        assertThrows(IllegalStateException.class, () -> tray.choose("Open"));
        assertThrows(IllegalStateException.class, () -> tray.tooltip("late"));
    }

    @Test
    @DisplayName("goes down with the backend, because a tray outliving its process is a leak")
    void closingTheBackendTakesTheTrayDown() {
        var tray = tray();

        backend.close();

        assertTrue(tray.isClosed());
    }
}
