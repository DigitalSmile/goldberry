package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// The calls a drag out makes on Windows outside the objects it hands over:
/// OLE's `DoDragDrop` and `OleInitialize` (ole32), clipboard format names, and
/// where the pointer is (user32).
///
/// UNVERIFIED: written against the Windows SDK's documentation; the Windows
/// lane's tests are the first to run it.
public final class OleCalls {

    /// `GA_ROOT`: a window's top-level ancestor.
    private static final int ROOT = 2;

    /// `POINT`: two `LONG`s.
    static final StructLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));

    /// `HRESULT OleInitialize(LPVOID)`.
    private static final FunctionDescriptor INITIALISE = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HRESULT DoDragDrop(LPDATAOBJECT, LPDROPSOURCE, DWORD, LPDWORD)`.
    private static final FunctionDescriptor DO_DRAG_DROP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    /// `UINT RegisterClipboardFormatW(LPCWSTR)`, and `BOOL GetCursorPos(LPPOINT)`.
    private static final FunctionDescriptor ONE_POINTER = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HWND WindowFromPoint(POINT)`.
    private static final FunctionDescriptor WINDOW_FROM_POINT =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, POINT));

    /// `HWND GetAncestor(HWND, UINT)`.
    private static final FunctionDescriptor ANCESTOR =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

    /// `BOOL ScreenToClient(HWND, LPPOINT)`.
    private static final FunctionDescriptor SCREEN_TO_CLIENT =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private final MethodHandle initialise;
    private final MethodHandle doDragDrop;
    private final MethodHandle registerFormat;
    private final MethodHandle cursor;
    private final MethodHandle windowFromPoint;
    private final MethodHandle ancestor;
    private final MethodHandle screenToClient;

    private OleCalls(
            MethodHandle initialise,
            MethodHandle doDragDrop,
            MethodHandle registerFormat,
            MethodHandle cursor,
            MethodHandle windowFromPoint,
            MethodHandle ancestor,
            MethodHandle screenToClient) {
        this.initialise = initialise;
        this.doDragDrop = doDragDrop;
        this.registerFormat = registerFormat;
        this.cursor = cursor;
        this.windowFromPoint = windowFromPoint;
        this.ancestor = ancestor;
        this.screenToClient = screenToClient;
    }

    /// The calls on this machine, or empty off Windows.
    @SuppressWarnings("restricted")
    public static Optional<OleCalls> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        try {
            var ole = SymbolLookup.libraryLookup("ole32.dll", Arena.global());
            var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            return Optional.of(new OleCalls(
                    Downcalls.link(INITIALISE).bindTo(ole.find("OleInitialize").orElseThrow()),
                    Downcalls.link(DO_DRAG_DROP).bindTo(ole.find("DoDragDrop").orElseThrow()),
                    Downcalls.link(ONE_POINTER)
                            .bindTo(user.find("RegisterClipboardFormatW").orElseThrow()),
                    Downcalls.link(ONE_POINTER).bindTo(user.find("GetCursorPos").orElseThrow()),
                    Downcalls.link(WINDOW_FROM_POINT)
                            .bindTo(user.find("WindowFromPoint").orElseThrow()),
                    Downcalls.link(ANCESTOR).bindTo(user.find("GetAncestor").orElseThrow()),
                    Downcalls.link(SCREEN_TO_CLIENT)
                            .bindTo(user.find("ScreenToClient").orElseThrow())));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// Makes OLE usable on the calling thread, answering whether it is. SDL
    /// has done this on its video thread already, and OLE counts a second
    /// call; one that finds the thread multithreaded answers false.
    public boolean initialise() {
        try {
            var result = (int) initialise.invokeExact(MemorySegment.NULL);
            return result >= 0;
        } catch (Throwable t) {
            throw Downcalls.failure("OleInitialize", t);
        }
    }

    /// The clipboard format registered under `name`, or empty when the system
    /// will not register one.
    public OptionalInt registerFormat(String name) {
        try (var arena = Arena.ofConfined()) {
            var format = (int) registerFormat.invokeExact(arena.allocateFrom(name, StandardCharsets.UTF_16LE));
            return format == 0 ? OptionalInt.empty() : OptionalInt.of(format);
        } catch (Throwable t) {
            throw Downcalls.failure("RegisterClipboardFormatW", t);
        }
    }

    /// Runs the drag: returns when it is over.
    ///
    /// @param data    the `IDataObject`
    /// @param source  the `IDropSource`
    /// @param allowed the `DROPEFFECT`s a target may choose from
    /// @param effect  where the effect the target chose is written, one `DWORD`
    /// @return `DRAGDROP_S_DROP`, `DRAGDROP_S_CANCEL`, or a failure
    public int doDragDrop(MemorySegment data, MemorySegment source, int allowed, MemorySegment effect) {
        try {
            return (int) doDragDrop.invokeExact(data, source, allowed, effect);
        } catch (Throwable t) {
            throw Downcalls.failure("DoDragDrop", t);
        }
    }

    /// The top-level window under the pointer and the pointer in its client
    /// area, in physical pixels, or null where the system will not say.
    public @Nullable Located locate() {
        try (var arena = Arena.ofConfined()) {
            var point = arena.allocate(POINT);
            if ((int) cursor.invokeExact(point) == 0) {
                return null;
            }
            var under = (MemorySegment) windowFromPoint.invokeExact(point);
            if (under.address() == 0) {
                return null;
            }
            var root = (MemorySegment) ancestor.invokeExact(under, ROOT);
            if (root.address() == 0) {
                root = under;
            }
            if ((int) screenToClient.invokeExact(root, point) == 0) {
                return null;
            }
            return new Located(root.address(), point.get(JAVA_INT, 0), point.get(JAVA_INT, 4));
        } catch (Throwable t) {
            throw Downcalls.failure("GetCursorPos", t);
        }
    }

    /// A top-level window and a point in its client area.
    ///
    /// @param window the `HWND`, as an address
    /// @param x      physical pixels from the client area's left edge
    /// @param y      from its top edge
    public record Located(long window, int x, int y) {}
}
