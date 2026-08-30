package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTray;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTrayEntryFlag;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTrayIcon;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTrayItem;

/// The tray description, and the tray itself where a desktop offers one.
///
/// Two halves, deliberately. The **description** is a value and is checked like
/// one — a submenu with no rows, a checkbox's flags, and the `0x80000000` that is
/// a negative `int` — none of which needs a display. The **tray** needs SDL's
/// video subsystem and a notification area, and neither is a thing CI has, so
/// that half proves what it can: the symbols are reachable, the calling
/// conventions are right, and absence is reported rather than thrown.
class SdlTrayTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("gives a command the BUTTON flag and nothing else")
    void commandIsAButton() {
        assertEquals(
                EnumSet.of(SdlTrayEntryFlag.BUTTON),
                SdlTrayItem.command("Open", checked -> {}).flags());
    }

    @Test
    @DisplayName("gives a checked checkbox both of its flags, and an unchecked one only its kind")
    void checkboxCarriesItsState() {
        assertEquals(
                EnumSet.of(SdlTrayEntryFlag.CHECKBOX, SdlTrayEntryFlag.CHECKED),
                SdlTrayItem.checkbox("Notify", true, checked -> {}).flags());
        assertEquals(
                EnumSet.of(SdlTrayEntryFlag.CHECKBOX),
                SdlTrayItem.checkbox("Notify", false, checked -> {}).flags());
    }

    @Test
    @DisplayName("names no kind for a separator, because SDL takes a null label for one")
    void separatorHasNoFlags() {
        assertTrue(SdlTrayItem.separator().flags().isEmpty());
    }

    @Test
    @DisplayName("adds DISABLED to whatever kind the row already is")
    void disabledKeepsItsKind() {
        assertEquals(
                EnumSet.of(SdlTrayEntryFlag.CHECKBOX, SdlTrayEntryFlag.CHECKED, SdlTrayEntryFlag.DISABLED),
                SdlTrayItem.checkbox("Notify", true, checked -> {}).disabled().flags());
    }

    @Test
    @DisplayName("refuses a submenu with no rows, and children on anything else")
    void submenusHaveChildrenAndNothingElseDoes() {
        assertThrows(IllegalArgumentException.class, () -> SdlTrayItem.submenu("Recent", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SdlTrayItem(
                        SdlTrayItem.Kind.COMMAND, "Open", true, false, null, List.of(SdlTrayItem.separator())));
    }

    @Test
    @DisplayName("masks DISABLED to the negative int SDL's Uint32 wants")
    void disabledIsTheHighBit() {
        // The whole reason the bits are held as a long: written as an int
        // literal this is -2147483648, and a mask assembled in an int is the
        // one place that difference is invisible until a platform disagrees.
        assertEquals(0x80000000, SdlTrayEntryFlag.mask(EnumSet.of(SdlTrayEntryFlag.DISABLED)));
        assertTrue(SdlTrayEntryFlag.mask(EnumSet.of(SdlTrayEntryFlag.DISABLED)) < 0);
        assertEquals(0x80000001, SdlTrayEntryFlag.mask(EnumSet.of(SdlTrayEntryFlag.BUTTON, SdlTrayEntryFlag.DISABLED)));
        assertEquals(0, SdlTrayEntryFlag.mask(EnumSet.noneOf(SdlTrayEntryFlag.class)));
    }

    @Test
    @DisplayName("names every SDL_TRAYENTRY_ constant the way the shim reports it")
    void flagsAreNamedForTheProbe() {
        assertEquals("SDL_TRAYENTRY_BUTTON", SdlTrayEntryFlag.BUTTON.nativeName());
        assertEquals("SDL_TRAYENTRY_CHECKED", SdlTrayEntryFlag.CHECKED.nativeName());
    }

    @Test
    @DisplayName("refuses an icon whose buffer cannot hold it")
    void iconChecksItsBuffer() {
        var direct = ByteBuffer.allocateDirect(16 * 16 * 4).order(ByteOrder.nativeOrder());

        assertEquals(64, SdlTrayIcon.of(direct, 16, 16).stride());

        assertThrows(
                IllegalArgumentException.class,
                () -> SdlTrayIcon.of(ByteBuffer.allocate(16 * 16 * 4), 16, 16),
                "a heap buffer has no address SDL could read");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SdlTrayIcon(direct, 16, 16, 32),
                "a stride of 32 cannot hold a 16-pixel row");
        assertThrows(
                IllegalArgumentException.class,
                () -> SdlTrayIcon.of(direct, 32, 32),
                "a 32x32 icon needs four times this buffer");
        assertThrows(IllegalArgumentException.class, () -> SdlTrayIcon.of(direct, 0, 16));
    }

    @Test
    @DisplayName("binds every tray symbol on the export list")
    void bindsItsSymbols() {
        if (!startVideo()) {
            return;
        }
        // Whatever this session has, the answer is an Optional and not an
        // UnsatisfiedLinkError -- which is the half of this that fails first
        // when goldberry.symbols and the binding disagree.
        var tray = SdlTray.open(
                null,
                "Goldberry",
                List.of(
                        SdlTrayItem.command("Open", checked -> {}),
                        SdlTrayItem.separator(),
                        SdlTrayItem.checkbox("Notifications", true, checked -> {}),
                        SdlTrayItem.submenu(
                                "Recent",
                                List.of(SdlTrayItem.command("report.pdf", checked -> {})
                                        .disabled())),
                        SdlTrayItem.command("Quit", checked -> {})));
        assertNotNull(tray);

        tray.ifPresent(open -> {
            assertFalse(open.isClosed());
            // Every call the wrapper offers, on a live tray: an icon it never
            // had, a tooltip it did, and then down. A platform that refuses one
            // of these does so silently, so what is being proven is the
            // crossing rather than the effect.
            open.tooltip("Goldberry — running");
            open.close();
            assertTrue(open.isClosed());
            open.close();
        });
    }

    /// Starts SDL's video subsystem, or reports that this machine has none.
    private static boolean startVideo() {
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
            return true;
        } catch (SdlException e) {
            return false;
        }
    }
}
