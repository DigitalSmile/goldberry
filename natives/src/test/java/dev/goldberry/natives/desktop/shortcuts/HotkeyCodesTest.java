package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What each platform calls a key and a modifier, which every backend's
/// grab is built from and which runs anywhere.
@DisplayName("hotkey codes")
class HotkeyCodesTest {

    @Nested
    @DisplayName("the key table")
    class Keys {

        @Test
        @DisplayName("gives every key a distinct keysym, keysym name and Windows code")
        void distinct() {
            var keys = HotkeyKey.values();
            assertAll(
                    () -> assertEquals(
                            keys.length,
                            Arrays.stream(keys)
                                    .map(HotkeyKey::keysym)
                                    .distinct()
                                    .count()),
                    () -> assertEquals(
                            keys.length,
                            Arrays.stream(keys)
                                    .map(HotkeyKey::keysymName)
                                    .distinct()
                                    .count()),
                    () -> assertEquals(
                            keys.length,
                            Arrays.stream(keys)
                                    .map(HotkeyKey::windowsVk)
                                    .distinct()
                                    .count()));
        }

        @Test
        @DisplayName("gives every key a Mac has a distinct key code, and Menu none")
        void mac() {
            var codes = new HashSet<Integer>();
            for (var key : HotkeyKey.values()) {
                if (key == HotkeyKey.MENU) {
                    assertEquals(-1, key.macKeyCode());
                } else {
                    assertTrue(key.macKeyCode() >= 0 && codes.add(key.macKeyCode()), key.toString());
                }
            }
        }

        @Test
        @DisplayName("spells letters and digits as their own keysyms, and Windows' as the upper case")
        void letters() {
            assertAll(
                    () -> assertEquals('q', HotkeyKey.Q.keysym()),
                    () -> assertEquals("q", HotkeyKey.Q.keysymName()),
                    () -> assertEquals('Q', HotkeyKey.Q.windowsVk()),
                    () -> assertEquals(0x0C, HotkeyKey.Q.macKeyCode()),
                    () -> assertEquals('7', HotkeyKey.DIGIT_7.keysym()),
                    () -> assertEquals(0xffc2, HotkeyKey.F5.keysym()),
                    () -> assertEquals(0x74, HotkeyKey.F5.windowsVk()));
        }
    }

    @Nested
    @DisplayName("a hotkey")
    class Hotkeys {

        @Test
        @DisplayName("writes its trigger as the XDG shortcuts specification does")
        void xdgTrigger() {
            assertAll(
                    () -> assertEquals("F9", new Hotkey("t", "", HotkeyKey.F9, 0).xdgTrigger()),
                    () -> assertEquals(
                            "CTRL+ALT+SHIFT+LOGO+space",
                            new Hotkey("t", "", HotkeyKey.SPACE, Hotkey.SHIFT | Hotkey.CTRL | Hotkey.ALT | Hotkey.META)
                                    .xdgTrigger()),
                    () -> assertEquals("LOGO+Prior", new Hotkey("t", "", HotkeyKey.PAGE_UP, Hotkey.META).xdgTrigger()));
        }

        @Test
        @DisplayName("refuses an empty id and modifier bits it does not know")
        void refuses() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Hotkey(" ", "", HotkeyKey.A, 0)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Hotkey("a", "", HotkeyKey.A, 16)));
        }
    }

    @Nested
    @DisplayName("on X11")
    class X11 {

        @Test
        @DisplayName("maps Shift, Ctrl, Alt and Meta to Shift, Control, Mod1 and Mod4")
        void modifiers() {
            assertAll(
                    () -> assertEquals(0, XlibHotkeys.xModifiers(0)),
                    () -> assertEquals(
                            XlibHotkeys.SHIFT_MASK
                                    | XlibHotkeys.CONTROL_MASK
                                    | XlibHotkeys.MOD1_MASK
                                    | XlibHotkeys.MOD4_MASK,
                            XlibHotkeys.xModifiers(Hotkey.SHIFT | Hotkey.CTRL | Hotkey.ALT | Hotkey.META)),
                    () -> assertEquals(XlibHotkeys.MOD4_MASK, XlibHotkeys.xModifiers(Hotkey.META)));
        }

        @Test
        @DisplayName("grabs each hotkey with and without Caps Lock and Num Lock")
        void lockVariants() {
            var ctrl = XlibHotkeys.CONTROL_MASK;
            assertArrayEquals(
                    new int[] {ctrl, ctrl | 2, ctrl | 16, ctrl | 2 | 16},
                    XlibHotkeys.lockVariants(ctrl, XlibHotkeys.MOD2_MASK));
        }

        @Test
        @DisplayName("finds Num Lock's modifier in the keyboard map, Mod2 where it is absent")
        void numLock() {
            try (var arena = Arena.ofConfined()) {
                // Eight modifiers, two keycodes each; Num Lock (77) on Mod3.
                var codes = arena.allocate(16);
                codes.set(JAVA_BYTE, 10, (byte) 77);
                assertEquals(1 << 5, XlibHotkeys.numLockMask(codes, 2, 77));
                assertEquals(XlibHotkeys.MOD2_MASK, XlibHotkeys.numLockMask(codes, 2, 78));
            }
        }

        @Test
        @DisplayName("matches a press on its modifiers, ignoring the locks")
        void matched() {
            var pressed = XlibHotkeys.CONTROL_MASK | 2 | 16;
            assertEquals(XlibHotkeys.CONTROL_MASK, pressed & XlibHotkeys.MATCHED);
        }

        @Test
        @DisplayName("a Wayland session is one with a Wayland display or that says so")
        void wayland() {
            assertAll(
                    () -> assertTrue(HotkeyBackend.isWayland("wayland-0", null)),
                    () -> assertTrue(HotkeyBackend.isWayland(null, "Wayland")),
                    () -> assertFalse(HotkeyBackend.isWayland("", "x11")),
                    () -> assertFalse(HotkeyBackend.isWayland(null, null)));
        }
    }

    @Nested
    @DisplayName("on Windows")
    class Windows {

        @Test
        @DisplayName("maps the modifiers to MOD_*, always with MOD_NOREPEAT")
        void modifiers() {
            assertAll(
                    () -> assertEquals(WindowsHotkeys.MOD_NOREPEAT, WindowsHotkeys.windowsModifiers(0)),
                    () -> assertEquals(
                            WindowsHotkeys.MOD_NOREPEAT
                                    | WindowsHotkeys.MOD_ALT
                                    | WindowsHotkeys.MOD_CONTROL
                                    | WindowsHotkeys.MOD_SHIFT
                                    | WindowsHotkeys.MOD_WIN,
                            WindowsHotkeys.windowsModifiers(Hotkey.SHIFT | Hotkey.CTRL | Hotkey.ALT | Hotkey.META)));
        }

        @Test
        @DisplayName("reads a key as down from GetAsyncKeyState's high bit alone")
        void down() {
            assertAll(
                    () -> assertTrue(WindowsHotkeys.isDown((short) 0x8000)),
                    () -> assertTrue(WindowsHotkeys.isDown((short) 0x8001)),
                    () -> assertFalse(WindowsHotkeys.isDown((short) 0x0001)),
                    () -> assertFalse(WindowsHotkeys.isDown((short) 0)));
        }

        @Test
        @DisplayName("lays out MSG as the 64-bit SDK does")
        void message() {
            assertAll(
                    () -> assertEquals(48, WindowsHotkeys.MSG_BYTES),
                    () -> assertEquals(8, WindowsHotkeys.MSG_MESSAGE),
                    () -> assertEquals(16, WindowsHotkeys.MSG_WPARAM));
        }
    }

    @Nested
    @DisplayName("on macOS")
    class Mac {

        @Test
        @DisplayName("maps Meta to Command, and the rest to Carbon's bits")
        void modifiers() {
            assertAll(
                    () -> assertEquals(CarbonHotkeys.CMD_KEY, CarbonHotkeys.carbonModifiers(Hotkey.META)),
                    () -> assertEquals(
                            CarbonHotkeys.CMD_KEY
                                    | CarbonHotkeys.SHIFT_KEY
                                    | CarbonHotkeys.OPTION_KEY
                                    | CarbonHotkeys.CONTROL_KEY,
                            CarbonHotkeys.carbonModifiers(Hotkey.SHIFT | Hotkey.CTRL | Hotkey.ALT | Hotkey.META)));
        }

        @Test
        @DisplayName("spells four-character codes as their big-endian integers")
        void fourCharCodes() {
            assertAll(
                    () -> assertEquals(0x6B657962, CarbonHotkeys.EVENT_CLASS_KEYBOARD),
                    () -> assertEquals(0x2D2D2D2D, CarbonHotkeys.PARAM_DIRECT_OBJECT),
                    () -> assertEquals(0x686B6964, CarbonHotkeys.TYPE_EVENT_HOT_KEY_ID),
                    () -> assertThrows(IllegalArgumentException.class, () -> CarbonHotkeys.fourCharCode("abc")));
        }

        @Test
        @DisplayName("passes EventHotKeyID by value as two 32-bit fields")
        void hotKeyId() {
            assertEquals(8, CarbonHotkeys.HOT_KEY_ID.byteSize());
        }
    }
}
