package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.Upcalls;

/// Notifications on Windows: the notification area's balloon,
/// `Shell_NotifyIconW` with `NIF_INFO`, which Windows 10 and 11 show as a
/// toast.
///
/// ## Why not a toast of its own
///
/// A real toast — `ToastNotificationManager` — is WinRT, and Windows shows one
/// only for an application with an **AppUserModelID** registered against a
/// Start-menu shortcut or a package. Registering that is installation, which
/// is the application's business and not the toolkit's. The balloon needs
/// neither: it belongs to an icon in the notification area, which this adds
/// for the first notification and takes away on [#close()].
///
/// ## Clicks
///
/// The icon belongs to a message-only window of this class's own, not to
/// SDL's, so the icon's messages never reach SDL's window procedure. Windows
/// posts them to the thread that made the window, the UI thread, and SDL's pump
/// dispatches every message of that thread, so they arrive here while the
/// application's events are pumped. A click on the balloon is remembered, and
/// [#poll()] answers it, as the Linux notifier does.
///
/// The structure is `NOTIFYICONDATAW` as the 64-bit Windows SDK lays it out,
/// 976 bytes, written field by field below.
///
/// UNVERIFIED: written against the Windows SDK's documentation and not yet run
/// on Windows. The structures' offsets and the message handling are checked by
/// tests that run anywhere.
public final class WindowsNotifier implements AutoCloseable {

    /// `BOOL Shell_NotifyIconW(DWORD dwMessage, PNOTIFYICONDATAW lpData)`.
    private static final FunctionDescriptor NOTIFY_ICON =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

    /// `HICON LoadIconW(HINSTANCE, LPCWSTR)`.
    private static final FunctionDescriptor LOAD_ICON =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    private static final int NIM_ADD = 0;
    private static final int NIM_MODIFY = 1;
    private static final int NIM_DELETE = 2;
    private static final int NIF_MESSAGE = 0x01;
    private static final int NIF_ICON = 0x02;
    private static final int NIF_TIP = 0x04;
    private static final int NIF_INFO = 0x10;
    private static final int NIIF_INFO = 0x01;

    /// `IDI_APPLICATION`, the generic application icon, as `MAKEINTRESOURCE`
    /// spells it: a number where a pointer goes.
    private static final long IDI_APPLICATION = 32512;

    /// The icon's id among this window's icons.
    private static final int ICON_ID = 0x6B6F;

    // NOTIFYICONDATAW, 64-bit.
    static final long SIZE = 976;
    private static final long CB_SIZE = 0;
    static final long HWND = 8;
    private static final long UID = 16;
    private static final long FLAGS = 20;
    static final long CALLBACK_MESSAGE = 24;
    static final long HICON = 32;
    static final long TIP = 40;
    private static final int TIP_CHARS = 128;
    static final long INFO = 304;
    private static final int INFO_CHARS = 256;
    static final long INFO_TITLE = 820;
    private static final int INFO_TITLE_CHARS = 64;
    static final long INFO_FLAGS = 948;

    private final MethodHandle notifyIcon;
    private final MethodHandle loadIcon;
    private final MessageWindow window;
    private final BalloonEvents events = new BalloonEvents();
    private long nextId;
    private boolean added;
    private boolean closed;

    private WindowsNotifier(MethodHandle notifyIcon, MethodHandle loadIcon, MessageWindow window) {
        this.notifyIcon = notifyIcon;
        this.loadIcon = loadIcon;
        this.window = window;
        WINDOWS.put(window.handle().address(), events);
    }

    /// A notifier with a message-only window of its own, or empty off Windows.
    /// Called on the UI thread: the window belongs to the thread that makes it,
    /// and that thread's pump is what delivers its messages.
    @SuppressWarnings("restricted")
    public static Optional<WindowsNotifier> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        try {
            var shell = SymbolLookup.libraryLookup("shell32.dll", Arena.global());
            var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            var window = MessageWindow.create().orElse(null);
            if (window == null) {
                return Optional.empty();
            }
            return Optional.of(new WindowsNotifier(
                    Downcalls.link(NOTIFY_ICON)
                            .bindTo(shell.find("Shell_NotifyIconW").orElseThrow()),
                    Downcalls.link(LOAD_ICON).bindTo(user.find("LoadIconW").orElseThrow()),
                    window));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// What happened to a balloon since the last [#poll()].
    ///
    /// @param id        the id [#notify] answered with
    /// @param activated true when the user clicked it, false when it went away
    ///                  otherwise: timed out, dismissed, or replaced
    public record Event(long id, boolean activated) {}

    /// Shows `title` and `body` in a balloon over this application's
    /// notification-area icon, adding the icon the first time.
    ///
    /// @param tooltip the icon's tooltip — the application's name
    /// @return the balloon's id, or 0 where the shell would not take it
    public long notify(String tooltip, String title, String body) {
        if (closed) {
            return 0;
        }
        try (var arena = Arena.ofConfined()) {
            if (!added) {
                var data = data(arena, NIF_ICON | NIF_TIP | NIF_MESSAGE);
                data.set(JAVA_INT, CALLBACK_MESSAGE, BalloonEvents.CALLBACK);
                data.set(ADDRESS, HICON, (MemorySegment)
                        call(loadIcon, MemorySegment.NULL, MemorySegment.ofAddress(IDI_APPLICATION)));
                text(data, TIP, TIP_CHARS, tooltip);
                if ((int) call(notifyIcon, NIM_ADD, data) == 0) {
                    return 0;
                }
                added = true;
            }
            var data = data(arena, NIF_INFO);
            text(data, INFO_TITLE, INFO_TITLE_CHARS, title);
            text(data, INFO, INFO_CHARS, body);
            data.set(JAVA_INT, INFO_FLAGS, NIIF_INFO);
            if ((int) call(notifyIcon, NIM_MODIFY, data) == 0) {
                return 0;
            }
            var id = ++nextId;
            events.shown(id);
            return id;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /// What has happened to the balloons since the last call: clicks and
    /// closes, heard while SDL pumped the UI thread's messages.
    public List<Event> poll() {
        return events.drain();
    }

    /// Takes the icon out of the notification area and destroys the window.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (added) {
            added = false;
            try (var arena = Arena.ofConfined()) {
                call(notifyIcon, NIM_DELETE, data(arena, 0));
            } catch (RuntimeException e) {
                // The shell has gone with the session; so has the icon.
            }
        }
        WINDOWS.remove(window.handle().address());
        window.close();
    }

    private MemorySegment data(Arena arena, int flags) {
        var data = arena.allocate(SIZE, 8);
        data.fill((byte) 0);
        data.set(JAVA_INT, CB_SIZE, (int) SIZE);
        data.set(ADDRESS, HWND, window.handle());
        data.set(JAVA_INT, UID, ICON_ID);
        data.set(JAVA_INT, FLAGS, flags);
        return data;
    }

    /// Writes `value` into a fixed `WCHAR` array, cut to fit with room for the
    /// terminator.
    private static void text(MemorySegment data, long offset, int chars, @Nullable String value) {
        var text = value == null ? "" : value;
        var length = Math.min(text.length(), chars - 1);
        for (var index = 0; index < length; index++) {
            data.set(JAVA_CHAR, offset + 2L * index, text.charAt(index));
        }
        data.set(JAVA_CHAR, offset + 2L * length, '\0');
    }

    private static Object call(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new IllegalStateException("a shell call failed", e);
        }
    }

    // ---- the message-only window

    /// Every notifier's events, by its window's handle, which is how the one
    /// window procedure finds them.
    private static final Map<Long, BalloonEvents> WINDOWS = new ConcurrentHashMap<>();

    /// `LRESULT CALLBACK WindowProc(HWND, UINT, WPARAM, LPARAM)`, which is also
    /// `DefWindowProcW`'s shape.
    private static final FunctionDescriptor WINDOW_PROCEDURE =
            Upcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

    private static final FunctionDescriptor DEFAULT_PROCEDURE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

    /// `ATOM RegisterClassExW(const WNDCLASSEXW *)`.
    private static final FunctionDescriptor REGISTER_CLASS =
            Downcalls.describe(FunctionDescriptor.of(JAVA_SHORT, ADDRESS));

    /// `HWND CreateWindowExW(DWORD, LPCWSTR, LPCWSTR, DWORD, int, int, int,
    /// int, HWND, HMENU, HINSTANCE, LPVOID)`.
    private static final FunctionDescriptor CREATE_WINDOW = Downcalls.describe(FunctionDescriptor.of(
            ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS,
            ADDRESS, ADDRESS));

    /// `BOOL DestroyWindow(HWND)`.
    private static final FunctionDescriptor DESTROY_WINDOW =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `HMODULE GetModuleHandleW(LPCWSTR)`.
    private static final FunctionDescriptor MODULE_HANDLE = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));

    /// `HWND_MESSAGE`: the parent that makes a window message-only.
    private static final long HWND_MESSAGE = -3;

    private static final String CLASS_NAME = "GoldberryNotificationWindow";

    // WNDCLASSEXW, 64-bit.
    static final long CLASS_SIZE = 80;
    static final long CLASS_PROCEDURE = 8;
    static final long CLASS_INSTANCE = 24;
    static final long CLASS_NAME_OFFSET = 64;

    /// The window procedure: the icon's messages go to the notifier whose
    /// window it is, everything else to `DefWindowProcW`. An upcall must never
    /// throw, so nothing escapes it.
    static long procedure(MemorySegment window, int message, long wParam, long lParam) {
        try {
            var events = WINDOWS.get(window.address());
            if (events != null && events.message(message, lParam)) {
                return 0;
            }
            var fallback = WindowClass.defaultProcedure;
            return fallback == null ? 0 : (long) fallback.invokeExact(window, message, wParam, lParam);
        } catch (Throwable t) {
            return 0;
        }
    }

    /// The window class, registered once per process, the first time a window
    /// is made.
    private static final class WindowClass {

        static volatile @Nullable MethodHandle defaultProcedure;
        private static @Nullable Registered registered;

        record Registered(MethodHandle create, MethodHandle destroy, MemorySegment name, MemorySegment instance) {}

        @SuppressWarnings("restricted")
        static synchronized Registered get() throws ReflectiveOperationException {
            if (registered != null) {
                return registered;
            }
            var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            var kernel = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
            defaultProcedure = Downcalls.link(DEFAULT_PROCEDURE)
                    .bindTo(user.find("DefWindowProcW").orElseThrow());
            var instance = (MemorySegment) call(
                    Downcalls.link(MODULE_HANDLE)
                            .bindTo(kernel.find("GetModuleHandleW").orElseThrow()),
                    MemorySegment.NULL);
            var target = MethodHandles.lookup()
                    .findStatic(
                            WindowsNotifier.class,
                            "procedure",
                            MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class));
            var stub = Linker.nativeLinker().upcallStub(target, WINDOW_PROCEDURE, Arena.global());
            var name = Arena.global().allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
            try (var arena = Arena.ofConfined()) {
                var windowClass = arena.allocate(CLASS_SIZE, 8);
                windowClass.fill((byte) 0);
                windowClass.set(JAVA_INT, 0, (int) CLASS_SIZE);
                windowClass.set(ADDRESS, CLASS_PROCEDURE, stub);
                windowClass.set(ADDRESS, CLASS_INSTANCE, instance);
                windowClass.set(ADDRESS, CLASS_NAME_OFFSET, name);
                // An atom of 0 is a class registered already, by an earlier
                // class loader in this process; creating the window says
                // whether it is usable.
                Downcalls.link(REGISTER_CLASS)
                        .bindTo(user.find("RegisterClassExW").orElseThrow())
                        .invokeWithArguments(windowClass);
            } catch (Throwable t) {
                throw new IllegalStateException("RegisterClassExW failed", t);
            }
            registered = new Registered(
                    Downcalls.link(CREATE_WINDOW)
                            .bindTo(user.find("CreateWindowExW").orElseThrow()),
                    Downcalls.link(DESTROY_WINDOW)
                            .bindTo(user.find("DestroyWindow").orElseThrow()),
                    name,
                    instance);
            return registered;
        }
    }

    /// A message-only window: never shown, never enumerated, there to be sent
    /// the icon's messages.
    private record MessageWindow(MemorySegment handle, MethodHandle destroy) {

        static Optional<MessageWindow> create() {
            try {
                var windowClass = WindowClass.get();
                var handle = (MemorySegment) call(
                        windowClass.create(),
                        0,
                        windowClass.name(),
                        windowClass.name(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        MemorySegment.ofAddress(HWND_MESSAGE),
                        MemorySegment.NULL,
                        windowClass.instance(),
                        MemorySegment.NULL);
                return handle.address() == 0
                        ? Optional.empty()
                        : Optional.of(new MessageWindow(handle, windowClass.destroy()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }

        void close() {
            try {
                call(destroy, handle);
            } catch (RuntimeException e) {
                // Gone with the thread already.
            }
        }
    }
}
