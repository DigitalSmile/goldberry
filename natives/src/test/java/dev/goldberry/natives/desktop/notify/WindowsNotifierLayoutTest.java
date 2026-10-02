package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The offsets [WindowsNotifier] writes `NOTIFYICONDATAW` at are the ones the
/// 64-bit Windows SDK lays it out with — checked here against the structure
/// spelled out field by field, because the notifier cannot be run anywhere
/// but Windows.
@DisplayName("the Windows notification structure")
class WindowsNotifierLayoutTest {

    /// `NOTIFYICONDATAW`, from `shellapi.h`, with the padding the x64 ABI puts
    /// before each pointer.
    private static final StructLayout NOTIFYICONDATAW = MemoryLayout.structLayout(
            JAVA_INT.withName("cbSize"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hWnd"),
            JAVA_INT.withName("uID"),
            JAVA_INT.withName("uFlags"),
            JAVA_INT.withName("uCallbackMessage"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hIcon"),
            MemoryLayout.sequenceLayout(128, JAVA_CHAR).withName("szTip"),
            JAVA_INT.withName("dwState"),
            JAVA_INT.withName("dwStateMask"),
            MemoryLayout.sequenceLayout(256, JAVA_CHAR).withName("szInfo"),
            JAVA_INT.withName("uTimeout"),
            MemoryLayout.sequenceLayout(64, JAVA_CHAR).withName("szInfoTitle"),
            JAVA_INT.withName("dwInfoFlags"),
            MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("guidItem"),
            ADDRESS.withName("hBalloonIcon"));

    private static long offset(String field) {
        return NOTIFYICONDATAW.byteOffset(PathElement.groupElement(field));
    }

    @Test
    @DisplayName("is written at the SDK's offsets, and is the SDK's size")
    void offsets() {
        assertEquals(WindowsNotifier.SIZE, NOTIFYICONDATAW.byteSize());
        assertEquals(WindowsNotifier.HWND, offset("hWnd"));
        assertEquals(WindowsNotifier.HICON, offset("hIcon"));
        assertEquals(WindowsNotifier.TIP, offset("szTip"));
        assertEquals(WindowsNotifier.INFO, offset("szInfo"));
        assertEquals(WindowsNotifier.INFO_TITLE, offset("szInfoTitle"));
        assertEquals(WindowsNotifier.INFO_FLAGS, offset("dwInfoFlags"));
    }

    @Test
    @DisplayName("is not offered off Windows")
    void onlyOnWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            assertTrue(WindowsNotifier.forWindow(1).isEmpty());
        }
    }
}
