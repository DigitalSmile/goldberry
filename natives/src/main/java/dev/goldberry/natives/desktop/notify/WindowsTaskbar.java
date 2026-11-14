package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.desktop.windows.Com;

/// The badge on Windows: an overlay icon on the window's taskbar button,
/// through `ITaskbarList3::SetOverlayIcon`, which is what Windows' own mail
/// and chat applications use for an unread count.
///
/// The picture is the caller's, straight ARGB, made into an `HICON` here: a
/// 32-bit top-down DIB section with an alpha channel and a mask the alpha
/// makes irrelevant. The taskbar keeps a copy, so the icon is destroyed as soon
/// as it is handed over.
///
/// UNVERIFIED: written against the Windows SDK's documentation and not yet run
/// on Windows. The structures' offsets and the vtable slot are checked by a
/// test that runs anywhere.
public final class WindowsTaskbar implements AutoCloseable {

    static final String CLSID_TASKBAR_LIST = "{56FDF344-FD6D-11D0-958A-006097C9A090}";
    static final String IID_TASKBAR_LIST3 = "{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}";

    /// `ITaskbarList::HrInit`: after `IUnknown`'s three.
    static final int HR_INIT = 3;

    /// `ITaskbarList3::SetOverlayIcon`: `IUnknown` (3), `ITaskbarList` (5),
    /// `ITaskbarList2` (1), then `ITaskbarList3`'s own nine before it.
    static final int SET_OVERLAY_ICON = 18;

    // BITMAPV5HEADER.
    static final long V5_SIZE = 124;
    static final long V5_WIDTH = 4;
    static final long V5_HEIGHT = 8;
    static final long V5_PLANES = 12;
    static final long V5_BIT_COUNT = 14;
    static final long V5_COMPRESSION = 16;
    static final long V5_RED_MASK = 40;
    static final long V5_GREEN_MASK = 44;
    static final long V5_BLUE_MASK = 48;
    static final long V5_ALPHA_MASK = 52;
    private static final int BI_BITFIELDS = 3;

    // ICONINFO, 64-bit.
    static final long ICON_INFO_SIZE = 32;
    static final long ICON_INFO_IS_ICON = 0;
    static final long ICON_INFO_MASK = 16;
    static final long ICON_INFO_COLOR = 24;

    /// `HRESULT HrInit(this)`.
    private static final FunctionDescriptor HR_INIT_SHAPE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HRESULT SetOverlayIcon(this, HWND, HICON, LPCWSTR)`.
    private static final FunctionDescriptor SET_OVERLAY_SHAPE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `HDC GetDC(HWND)` and `HICON CreateIconIndirect(PICONINFO)`.
    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));

    /// `int ReleaseDC(HWND, HDC)`.
    private static final FunctionDescriptor I_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `BOOL DestroyIcon(HICON)` and `BOOL DeleteObject(HGDIOBJ)`.
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HBITMAP CreateDIBSection(HDC, const BITMAPINFO *, UINT, void **, HANDLE, DWORD)`.
    private static final FunctionDescriptor CREATE_DIB_SECTION =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    /// `HBITMAP CreateBitmap(int, int, UINT, UINT, const void *)`.
    private static final FunctionDescriptor CREATE_BITMAP =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

    private final Com com;
    private final MemorySegment taskbar;
    private final MethodHandle setOverlay = Downcalls.link(SET_OVERLAY_SHAPE);
    private final MethodHandle getDc;
    private final MethodHandle releaseDc;
    private final MethodHandle createIcon;
    private final MethodHandle destroyIcon;
    private final MethodHandle createDibSection;
    private final MethodHandle createBitmap;
    private final MethodHandle deleteObject;
    private boolean closed;

    private WindowsTaskbar(Com com, MemorySegment taskbar, SymbolLookup user, SymbolLookup gdi) {
        this.com = com;
        this.taskbar = taskbar;
        this.getDc = bind(user, "GetDC", P_P);
        this.releaseDc = bind(user, "ReleaseDC", I_PP);
        this.createIcon = bind(user, "CreateIconIndirect", P_P);
        this.destroyIcon = bind(user, "DestroyIcon", I_P);
        this.createDibSection = bind(gdi, "CreateDIBSection", CREATE_DIB_SECTION);
        this.createBitmap = bind(gdi, "CreateBitmap", CREATE_BITMAP);
        this.deleteObject = bind(gdi, "DeleteObject", I_P);
    }

    /// The taskbar, or empty off Windows or where the shell has none (a
    /// server core, a kiosk). Called on the UI thread, which COM is
    /// initialised on here if nobody did it first.
    @SuppressWarnings("restricted")
    public static Optional<WindowsTaskbar> open() {
        var com = Com.open().orElse(null);
        if (com == null || !com.initialise()) {
            return Optional.empty();
        }
        try {
            var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            var gdi = SymbolLookup.libraryLookup("gdi32.dll", Arena.global());
            var taskbar = com.create(CLSID_TASKBAR_LIST, IID_TASKBAR_LIST3);
            if (taskbar.address() == 0) {
                return Optional.empty();
            }
            if (!Com.succeeded((int) Com.call(taskbar, HR_INIT, Downcalls.link(HR_INIT_SHAPE)))) {
                com.release(taskbar);
                return Optional.empty();
            }
            return Optional.of(new WindowsTaskbar(com, taskbar, user, gdi));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// Puts `argb`, a `width` × `height` picture of straight-alpha
    /// `0xAARRGGBB` pixels row by row, over `window`'s taskbar button.
    ///
    /// @param window      the top-level `HWND`
    /// @param description what a screen reader says for it: the count
    /// @return false where the shell would not take it
    public boolean overlay(long window, int width, int height, int[] argb, String description) {
        Objects.requireNonNull(argb, "argb");
        if (width <= 0 || height <= 0 || argb.length != width * height) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " picture has " + width * height + " pixels, not " + argb.length);
        }
        if (closed || window == 0) {
            return false;
        }
        var icon = icon(width, height, argb);
        if (icon.address() == 0) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            return Com.succeeded((int) Com.call(
                    taskbar,
                    SET_OVERLAY_ICON,
                    setOverlay,
                    MemorySegment.ofAddress(window),
                    icon,
                    arena.allocateFrom(description, StandardCharsets.UTF_16LE)));
        } catch (RuntimeException e) {
            return false;
        } finally {
            call(destroyIcon, icon);
        }
    }

    /// Takes the overlay off `window`'s taskbar button.
    public boolean clear(long window) {
        if (closed || window == 0) {
            return false;
        }
        try {
            return Com.succeeded((int) Com.call(
                    taskbar,
                    SET_OVERLAY_ICON,
                    setOverlay,
                    MemorySegment.ofAddress(window),
                    MemorySegment.NULL,
                    MemorySegment.NULL));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            com.release(taskbar);
        }
    }

    /// An `HICON` of the picture, or NULL. The caller destroys it.
    @SuppressWarnings("restricted")
    private MemorySegment icon(int width, int height, int[] argb) {
        try (var arena = Arena.ofConfined()) {
            var header = arena.allocate(V5_SIZE, 4);
            header.fill((byte) 0);
            header.set(JAVA_INT, 0, (int) V5_SIZE);
            header.set(JAVA_INT, V5_WIDTH, width);
            // Negative: top-down, so the rows go in in the order they are given.
            header.set(JAVA_INT, V5_HEIGHT, -height);
            header.set(JAVA_SHORT, V5_PLANES, (short) 1);
            header.set(JAVA_SHORT, V5_BIT_COUNT, (short) 32);
            header.set(JAVA_INT, V5_COMPRESSION, BI_BITFIELDS);
            header.set(JAVA_INT, V5_RED_MASK, 0x00FF0000);
            header.set(JAVA_INT, V5_GREEN_MASK, 0x0000FF00);
            header.set(JAVA_INT, V5_BLUE_MASK, 0x000000FF);
            header.set(JAVA_INT, V5_ALPHA_MASK, 0xFF000000);
            var bits = arena.allocate(ADDRESS);
            var screen = (MemorySegment) call(getDc, MemorySegment.NULL);
            var color = (MemorySegment) call(createDibSection, screen, header, 0, bits, MemorySegment.NULL, 0);
            call(releaseDc, MemorySegment.NULL, screen);
            if (color.address() == 0) {
                return MemorySegment.NULL;
            }
            var mask = (MemorySegment) call(createBitmap, width, height, 1, 1, MemorySegment.NULL);
            try {
                // A little-endian 0xAARRGGBB is B, G, R, A in memory: the DIB's
                // own order, so the pixels go in as they are.
                var pixels = bits.get(ADDRESS, 0).reinterpret(4L * argb.length);
                MemorySegment.copy(argb, 0, pixels, JAVA_INT, 0, argb.length);
                var info = arena.allocate(ICON_INFO_SIZE, 8);
                info.fill((byte) 0);
                info.set(JAVA_INT, ICON_INFO_IS_ICON, 1);
                info.set(ADDRESS, ICON_INFO_MASK, mask);
                info.set(ADDRESS, ICON_INFO_COLOR, color);
                return (MemorySegment) call(createIcon, info);
            } finally {
                call(deleteObject, color);
                if (mask.address() != 0) {
                    call(deleteObject, mask);
                }
            }
        }
    }

    private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
        return Downcalls.link(shape)
                .bindTo(lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("no " + name)));
    }

    private static Object call(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new IllegalStateException("a taskbar call failed", e);
        }
    }
}
