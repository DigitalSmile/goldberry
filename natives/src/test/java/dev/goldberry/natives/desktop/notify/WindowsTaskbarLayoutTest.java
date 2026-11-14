package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The structures [WindowsTaskbar] writes and the vtable slots it calls, held
/// to the Windows SDK's headers, because the taskbar exists only on Windows.
@DisplayName("the Windows taskbar overlay")
class WindowsTaskbarLayoutTest {

    /// `BITMAPV5HEADER`, from `wingdi.h`. `bV5Endpoints` is a `CIEXYZTRIPLE`,
    /// nine `LONG`s.
    private static final StructLayout BITMAPV5HEADER = MemoryLayout.structLayout(
            JAVA_INT.withName("bV5Size"),
            JAVA_INT.withName("bV5Width"),
            JAVA_INT.withName("bV5Height"),
            JAVA_SHORT.withName("bV5Planes"),
            JAVA_SHORT.withName("bV5BitCount"),
            JAVA_INT.withName("bV5Compression"),
            JAVA_INT.withName("bV5SizeImage"),
            JAVA_INT.withName("bV5XPelsPerMeter"),
            JAVA_INT.withName("bV5YPelsPerMeter"),
            JAVA_INT.withName("bV5ClrUsed"),
            JAVA_INT.withName("bV5ClrImportant"),
            JAVA_INT.withName("bV5RedMask"),
            JAVA_INT.withName("bV5GreenMask"),
            JAVA_INT.withName("bV5BlueMask"),
            JAVA_INT.withName("bV5AlphaMask"),
            JAVA_INT.withName("bV5CSType"),
            MemoryLayout.sequenceLayout(9, JAVA_INT).withName("bV5Endpoints"),
            JAVA_INT.withName("bV5GammaRed"),
            JAVA_INT.withName("bV5GammaGreen"),
            JAVA_INT.withName("bV5GammaBlue"),
            JAVA_INT.withName("bV5Intent"),
            JAVA_INT.withName("bV5ProfileData"),
            JAVA_INT.withName("bV5ProfileSize"),
            JAVA_INT.withName("bV5Reserved"));

    /// `ICONINFO`, from `winuser.h`, with the padding before the first handle.
    private static final StructLayout ICONINFO = MemoryLayout.structLayout(
            JAVA_INT.withName("fIcon"),
            JAVA_INT.withName("xHotspot"),
            JAVA_INT.withName("yHotspot"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("hbmMask"),
            ADDRESS.withName("hbmColor"));

    @Test
    @DisplayName("writes the bitmap header at the SDK's offsets")
    void bitmapHeader() {
        assertEquals(WindowsTaskbar.V5_SIZE, BITMAPV5HEADER.byteSize());
        assertEquals(WindowsTaskbar.V5_WIDTH, offset(BITMAPV5HEADER, "bV5Width"));
        assertEquals(WindowsTaskbar.V5_HEIGHT, offset(BITMAPV5HEADER, "bV5Height"));
        assertEquals(WindowsTaskbar.V5_PLANES, offset(BITMAPV5HEADER, "bV5Planes"));
        assertEquals(WindowsTaskbar.V5_BIT_COUNT, offset(BITMAPV5HEADER, "bV5BitCount"));
        assertEquals(WindowsTaskbar.V5_COMPRESSION, offset(BITMAPV5HEADER, "bV5Compression"));
        assertEquals(WindowsTaskbar.V5_RED_MASK, offset(BITMAPV5HEADER, "bV5RedMask"));
        assertEquals(WindowsTaskbar.V5_GREEN_MASK, offset(BITMAPV5HEADER, "bV5GreenMask"));
        assertEquals(WindowsTaskbar.V5_BLUE_MASK, offset(BITMAPV5HEADER, "bV5BlueMask"));
        assertEquals(WindowsTaskbar.V5_ALPHA_MASK, offset(BITMAPV5HEADER, "bV5AlphaMask"));
    }

    @Test
    @DisplayName("writes the icon structure at the SDK's offsets")
    void iconInfo() {
        assertEquals(WindowsTaskbar.ICON_INFO_SIZE, ICONINFO.byteSize());
        assertEquals(WindowsTaskbar.ICON_INFO_IS_ICON, offset(ICONINFO, "fIcon"));
        assertEquals(WindowsTaskbar.ICON_INFO_MASK, offset(ICONINFO, "hbmMask"));
        assertEquals(WindowsTaskbar.ICON_INFO_COLOR, offset(ICONINFO, "hbmColor"));
    }

    /// The vtable of `ITaskbarList3` in declaration order, from `shobjidl_core.h`:
    /// each interface's methods after the one it extends.
    @Test
    @DisplayName("calls HrInit and SetOverlayIcon at their vtable slots")
    void slots() {
        var vtable = List.of(
                // IUnknown
                "QueryInterface",
                "AddRef",
                "Release",
                // ITaskbarList
                "HrInit",
                "AddTab",
                "DeleteTab",
                "ActivateTab",
                "SetActiveAlt",
                // ITaskbarList2
                "MarkFullscreenWindow",
                // ITaskbarList3
                "SetProgressValue",
                "SetProgressState",
                "RegisterTab",
                "UnregisterTab",
                "SetTabOrder",
                "SetTabActive",
                "ThumbBarAddButtons",
                "ThumbBarUpdateButtons",
                "ThumbBarSetImageList",
                "SetOverlayIcon",
                "SetThumbnailTooltip",
                "SetThumbnailClip");

        assertEquals(vtable.indexOf("HrInit"), WindowsTaskbar.HR_INIT);
        assertEquals(vtable.indexOf("SetOverlayIcon"), WindowsTaskbar.SET_OVERLAY_ICON);
    }

    private static long offset(StructLayout layout, String field) {
        return layout.byteOffset(PathElement.groupElement(field));
    }
}
