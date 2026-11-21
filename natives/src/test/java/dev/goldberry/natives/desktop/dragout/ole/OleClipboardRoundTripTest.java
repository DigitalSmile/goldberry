package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import dev.goldberry.natives.desktop.windows.Com;
import dev.goldberry.natives.desktop.windows.ComServer;
import dev.goldberry.natives.desktop.windows.Win32GlobalMemory;

/// The data object as OLE itself calls it: put on the clipboard, read back
/// through what `OleGetClipboard` answers, and handed a picture by the shell's
/// drag image helper. Windows only; the rest of its tests run everywhere.
@SuppressWarnings("restricted")
@DisplayName("a drag's IDataObject, through OLE")
@EnabledOnOs(OS.WINDOWS)
class OleClipboardRoundTripTest {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle GET_DATA =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle RELEASE = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    @Test
    @DisplayName("is read back through OLE's clipboard, format and bytes intact")
    void clipboard() throws Throwable {
        var calls = OleCalls.open().orElseThrow();
        assertTrue(calls.initialise(), "OLE starts on the test's thread");
        var memory = Win32GlobalMemory.open().orElseThrow();
        var ole = SymbolLookup.libraryLookup("ole32.dll", Arena.global());
        var setClipboard = LINKER.downcallHandle(
                ole.find("OleSetClipboard").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS));
        var getClipboard = LINKER.downcallHandle(
                ole.find("OleGetClipboard").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS));

        var data = new OleDataObject(
                memory,
                List.of(new OleDataObject.Offered(
                        OleStructs.CF_UNICODETEXT, () -> "goldberry\0".getBytes(StandardCharsets.UTF_16LE))));
        try (var arena = Arena.ofConfined()) {
            assertEquals(ComServer.S_OK, (int) setClipboard.invokeExact(data.pointer()));
            var out = arena.allocate(ADDRESS);
            assertEquals(ComServer.S_OK, (int) getClipboard.invokeExact(out));
            var clipboard = out.get(ADDRESS, 0);
            try {
                var format = arena.allocate(OleStructs.FORMATETC_SIZE, 8);
                OleStructs.FormatEtc.write(format, OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL);
                var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);

                assertEquals(ComServer.S_OK, (int) Com.call(clipboard, 3, GET_DATA, format, medium));
                var bytes = memory.read(OleStructs.Medium.read(medium).handle());
                var text = new String(bytes, StandardCharsets.UTF_16LE);
                assertEquals("goldberry", text.substring(0, text.indexOf('\0')));
                memory.releaseMedium(medium);
            } finally {
                Com.call(clipboard, Com.RELEASE, RELEASE);
                var _ = (int) setClipboard.invokeExact(MemorySegment.NULL);
            }
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("takes the picture the shell's drag image helper sets on it")
    void picture() {
        assertTrue(OleCalls.open().orElseThrow().initialise());
        var data = new OleDataObject(
                Win32GlobalMemory.open().orElseThrow(),
                List.of(new OleDataObject.Offered(OleStructs.CF_UNICODETEXT, () -> new byte[] {0, 0})));
        try {
            var pixels = ByteBuffer.allocateDirect(8 * 8 * 4);
            for (var index = 0; index < 64; index++) {
                pixels.putInt(index * 4, 0x80402010);
            }

            assertTrue(OleDragImage.attach(data.pointer(), 8, 8, 32, pixels, 2, 3));
            assertFalse(data.storedFormats().isEmpty(), "the helper keeps the picture on the data object");
        } finally {
            data.release();
        }
    }
}
