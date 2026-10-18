package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.desktop.SdlTray;
import dev.goldberry.natives.sdl.desktop.SdlTrayItem;

/// A real tray, clicked as its desktop's shell would click it.
///
/// Nothing can click a shell's menu from outside it, so the click is SDL's own
/// `SDL_ClickTrayEntry`, which every platform implements in the shell's order: a
/// checkbox is toggled, then the row's callback runs. What is exercised is
/// everything after the shell: the platform's entries, the upcall stubs, the
/// row's handler, and a menu changed while the icon is up.
///
/// Skips where there is no notification area, which is most of CI. The Windows
/// lane requires one (`goldberry.tray.required`); macOS runs these on the JVM's
/// first thread, through [SdlTrayFirstThreadTest].
class SdlTrayLiveTest {

    private final List<String> heard = new ArrayList<>();
    private SdlTray tray;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void takeItDown() {
        if (tray != null) {
            tray.close();
        }
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    private SdlTrayItem command(String label) {
        return SdlTrayItem.command(label, checked -> heard.add(label));
    }

    private SdlTrayItem checkbox(String label, boolean checked) {
        return SdlTrayItem.checkbox(label, checked, now -> heard.add(label + "=" + now));
    }

    @Test
    @DisplayName("a click runs the row's handler, on the thread that opened the tray")
    void aClickRunsTheHandler() {
        var thread = new AtomicReference<Thread>();
        tray = TrayRequirement.open(
                List.of(SdlTrayItem.command("Open", checked -> thread.set(Thread.currentThread())), command("Quit")));

        tray.click("Open");

        assertSame(Thread.currentThread(), thread.get(), "SDL calls back from inside the caller's own call");
        tray.click("Quit");
        assertEquals(List.of("Quit"), heard);
    }

    @Test
    @DisplayName("a checkbox is toggled before its handler hears the new state")
    void aCheckboxTogglesFirst() {
        tray = TrayRequirement.open(List.of(checkbox("Notifications", true)));

        tray.click("Notifications");

        assertEquals(List.of("Notifications=false"), heard);
        assertFalse(tray.isChecked("Notifications"));
    }

    @Test
    @DisplayName("rows of the same shape change in place: a label, a tick and a handler")
    void sameShapeInPlace() {
        tray = TrayRequirement.open(List.of(command("Open"), checkbox("Notifications", false)));

        tray.rows(List.of(command("Open again"), checkbox("Notifications", true)));
        tray.click("Open again");

        assertEquals(List.of("Open again"), heard);
        assertTrue(tray.isChecked("Notifications"), "the description's tick, set without a click");
        assertThrows(IllegalArgumentException.class, () -> tray.click("Open"), "the old label is gone");
    }

    @Test
    @DisplayName("rows of another shape replace the menu under the same icon")
    void anotherShape() {
        tray = TrayRequirement.open(List.of(command("Open")));

        tray.rows(List.of(
                command("Open"),
                SdlTrayItem.separator(),
                SdlTrayItem.submenu("Recent", List.of(command("report.pdf")))));
        tray.click("Recent/report.pdf");
        tray.click("Open");

        assertEquals(List.of("report.pdf", "Open"), heard);
    }

    @Test
    @DisplayName("a handler that declines puts the tick back, after SDL's callback has returned")
    void aHandlerDeclines() {
        var self = new AtomicReference<SdlTray>();
        var declining = SdlTrayItem.checkbox("Notifications", true, now -> {
            heard.add("asked " + now);
            self.get().rows(List.of(SdlTrayItem.checkbox("Notifications", true, again -> heard.add("again"))));
        });
        tray = TrayRequirement.open(List.of(declining));
        self.set(tray);

        tray.click("Notifications");

        assertEquals(List.of("asked false"), heard);
        assertTrue(tray.isChecked("Notifications"), "described as ticked, so ticked");
        tray.click("Notifications");
        assertEquals(List.of("asked false", "again"), heard, "and the new handler is the one a click runs");
    }

    @Test
    @DisplayName("a handler may replace the whole menu, including the row it is running for")
    void aHandlerReshapes() {
        var self = new AtomicReference<SdlTray>();
        var reshaping = SdlTrayItem.command("Sign in", checked -> {
            heard.add("Sign in");
            self.get().rows(List.of(command("Account"), SdlTrayItem.separator(), command("Sign out")));
        });
        tray = TrayRequirement.open(List.of(reshaping));
        self.set(tray);

        tray.click("Sign in");
        tray.click("Sign out");

        assertEquals(List.of("Sign in", "Sign out"), heard);
    }
}
