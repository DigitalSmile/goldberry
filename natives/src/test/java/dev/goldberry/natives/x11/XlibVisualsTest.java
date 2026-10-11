package dev.goldberry.natives.x11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The half of [XlibVisuals] that needs no X server; the half that does is
/// `Sdl3PopupTest`'s, which asks a real popup whether it can be see-through.
class XlibVisualsTest {

    @Test
    @DisplayName("writes a visual id the way SDL's strtol reads it, in hexadecimal")
    void hintValue() {
        assertEquals("0x75", XlibVisuals.hintValue(0x75));
        assertEquals("0x21", XlibVisuals.hintValue(33));
    }

    @Test
    @DisplayName("refuses an id no visual has")
    void refusesANonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> XlibVisuals.hintValue(0));
        assertThrows(IllegalArgumentException.class, () -> XlibVisuals.hintValue(-1));
    }

    /// `sizeof(XVisualInfo)` and `offsetof(XVisualInfo, visualid)` on LP64,
    /// which is what XMatchVisualInfo writes into.
    @Test
    @DisplayName("lays XVisualInfo out as an LP64 compiler does")
    void visualInfoLayout() {
        assertEquals(64, XlibVisuals.VISUAL_INFO.byteSize());
        assertEquals(8, XlibVisuals.VISUAL_INFO.byteOffset(PathElement.groupElement("visualid")));
        assertEquals(20, XlibVisuals.VISUAL_INFO.byteOffset(PathElement.groupElement("depth")));
        assertEquals(32, XlibVisuals.VISUAL_INFO.byteOffset(PathElement.groupElement("red_mask")));
    }

    @Test
    @DisplayName("has no visual to offer without a display, and asks nothing")
    void noDisplayNoVisual() {
        assertTrue(XlibVisuals.alphaVisual(MemorySegment.NULL, 0).isEmpty());
    }
}
