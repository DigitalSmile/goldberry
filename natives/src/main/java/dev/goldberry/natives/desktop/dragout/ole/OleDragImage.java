package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.ByteBuffer;
import java.util.Locale;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.desktop.windows.Com;

/// The picture under the pointer, through the shell's drag image helper:
/// premultiplied BGRA into a top-down DIB section, handed to
/// `IDragSourceHelper::InitializeFromBitmap`, which stores it on the data
/// object for every target's drop helper to draw.
///
/// Best effort. Without a picture the pointer keeps OLE's own cursors, and
/// the drag works the same.
///
/// UNVERIFIED: written against the Windows SDK's documentation.
public final class OleDragImage {

    private static final Logger LOG = Logs.of(OleDragImage.class);

    /// `CLSID_DragDropHelper`.
    static final String HELPER = "{4657278A-411B-11D2-839A-00C04FD918D0}";

    /// `IID_IDragSourceHelper`.
    static final String SOURCE_HELPER = "{DE5BF786-477A-11D2-839D-00C04FD918D0}";

    /// `IDragSourceHelper::InitializeFromBitmap`'s slot.
    private static final int INITIALISE_FROM_BITMAP = 3;

    /// `CLR_NONE`: no colour key, the alpha channel says what shows.
    private static final int NO_COLOUR_KEY = 0xFFFFFFFF;

    /// `sizeof(BITMAPINFOHEADER)`.
    static final int INFO_HEADER_SIZE = 40;

    /// `sizeof(SHDRAGIMAGE)`: `SIZE` at 0, `POINT` at 8, `HBITMAP` at 16,
    /// `COLORREF` at 24.
    static final long DRAG_IMAGE_SIZE = 32;

    /// `HBITMAP CreateDIBSection(HDC, const BITMAPINFO *, UINT, void **,
    /// HANDLE, DWORD)`.
    private static final FunctionDescriptor CREATE_DIB =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    /// `BOOL DeleteObject(HGDIOBJ)`.
    private static final FunctionDescriptor DELETE_OBJECT =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HRESULT InitializeFromBitmap(this, LPSHDRAGIMAGE, IDataObject *)`.
    private static final FunctionDescriptor INITIALISE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    private OleDragImage() {}

    /// The `BITMAPINFOHEADER` of a top-down, 32-bit, uncompressed picture.
    static void writeInfoHeader(MemorySegment header, int width, int height) {
        header.fill((byte) 0);
        header.set(JAVA_INT, 0, INFO_HEADER_SIZE);
        header.set(JAVA_INT, 4, width);
        // Negative: the first row is the top one, as the picture's is.
        header.set(JAVA_INT, 8, -height);
        header.set(JAVA_SHORT, 12, (short) 1);
        header.set(JAVA_SHORT, 14, (short) 32);
    }

    /// Hands `pixels` to the shell as the picture of the drag of `data`,
    /// answering whether it took it.
    ///
    /// @param pixels   premultiplied BGRA rows of `stride` bytes
    /// @param hotspotX where the pointer is in the picture
    /// @param hotspotY the same, from the top
    @SuppressWarnings("restricted")
    public static boolean attach(
            MemorySegment data, int width, int height, int stride, ByteBuffer pixels, int hotspotX, int hotspotY) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        var com = Com.open();
        if (com.isEmpty()) {
            return false;
        }
        var helper = com.get().create(HELPER, SOURCE_HELPER);
        if (helper.address() == 0) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            var gdi = SymbolLookup.libraryLookup("gdi32.dll", arena);
            var bitmap = bitmap(gdi, arena, width, height, stride, pixels);
            if (bitmap.address() == 0) {
                return false;
            }
            var image = arena.allocate(DRAG_IMAGE_SIZE, 8);
            image.fill((byte) 0);
            image.set(JAVA_INT, 0, width);
            image.set(JAVA_INT, 4, height);
            image.set(JAVA_INT, 8, hotspotX);
            image.set(JAVA_INT, 12, hotspotY);
            image.set(ADDRESS, 16, bitmap);
            image.set(JAVA_INT, 24, NO_COLOUR_KEY);
            var result = (int) Com.call(helper, INITIALISE_FROM_BITMAP, Downcalls.link(INITIALISE), image, data);
            if (!Com.succeeded(result)) {
                // The helper owns the bitmap only once it has taken it.
                delete(gdi, bitmap);
                LOG.debug("the shell's drag image helper refused the picture: 0x{}", Integer.toHexString(result));
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            LOG.debug("a drag's picture could not be handed to the shell", e);
            return false;
        } finally {
            com.get().release(helper);
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment bitmap(
            SymbolLookup gdi, Arena arena, int width, int height, int stride, ByteBuffer pixels) {
        var info = arena.allocate(INFO_HEADER_SIZE, 4);
        writeInfoHeader(info, width, height);
        var bits = arena.allocate(ADDRESS);
        MemorySegment bitmap;
        try {
            bitmap = (MemorySegment) Downcalls.link(CREATE_DIB)
                    .bindTo(gdi.find("CreateDIBSection").orElseThrow())
                    .invokeExact(MemorySegment.NULL, info, 0, bits, MemorySegment.NULL, 0);
        } catch (Throwable t) {
            throw Downcalls.failure("CreateDIBSection", t);
        }
        if (bitmap.address() == 0) {
            return MemorySegment.NULL;
        }
        var target = bits.get(ADDRESS, 0).reinterpret((long) width * height * 4);
        var source = MemorySegment.ofBuffer(pixels);
        for (var row = 0; row < height; row++) {
            MemorySegment.copy(
                    source, JAVA_BYTE, (long) row * stride, target, JAVA_BYTE, (long) row * width * 4, width * 4L);
        }
        return bitmap;
    }

    private static void delete(SymbolLookup gdi, MemorySegment bitmap) {
        try {
            var _ = (int) Downcalls.link(DELETE_OBJECT)
                    .bindTo(gdi.find("DeleteObject").orElseThrow())
                    .invokeExact(bitmap);
        } catch (Throwable t) {
            LOG.debug("a drag picture's bitmap could not be deleted", t);
        }
    }
}
