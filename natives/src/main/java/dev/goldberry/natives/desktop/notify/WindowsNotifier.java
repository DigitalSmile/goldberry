package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

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
/// Clicks are not reported. Windows sends `NIN_BALLOONUSERCLICK` to the
/// window the icon names, and that window is SDL's, whose window procedure
/// does not know the message.
///
/// The structure is `NOTIFYICONDATAW` as the 64-bit Windows SDK lays it out,
/// 976 bytes, written field by field below.
///
/// UNVERIFIED: written against the Windows SDK's documentation and not yet run
/// on Windows.
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
    private final long window;
    private boolean added;

    private WindowsNotifier(MethodHandle notifyIcon, MethodHandle loadIcon, long window) {
        this.notifyIcon = notifyIcon;
        this.loadIcon = loadIcon;
        this.window = window;
    }

    /// A notifier whose icon belongs to `window`, an `HWND`, or empty off
    /// Windows.
    @SuppressWarnings("restricted")
    public static Optional<WindowsNotifier> forWindow(long window) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") || window == 0) {
            return Optional.empty();
        }
        try {
            var shell = SymbolLookup.libraryLookup("shell32.dll", Arena.global());
            var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
            return Optional.of(new WindowsNotifier(
                    Downcalls.link(NOTIFY_ICON)
                            .bindTo(shell.find("Shell_NotifyIconW").orElseThrow()),
                    Downcalls.link(LOAD_ICON).bindTo(user.find("LoadIconW").orElseThrow()),
                    window));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// Shows `title` and `body` in a balloon over this application's
    /// notification-area icon, adding the icon the first time.
    ///
    /// @param tooltip the icon's tooltip — the application's name
    /// @return false where the shell would not take it
    public boolean notify(String tooltip, String title, String body) {
        try (var arena = Arena.ofConfined()) {
            if (!added) {
                var data = data(arena, NIF_ICON | NIF_TIP);
                data.set(ADDRESS, HICON, (MemorySegment)
                        call(loadIcon, MemorySegment.NULL, MemorySegment.ofAddress(IDI_APPLICATION)));
                text(data, TIP, TIP_CHARS, tooltip);
                if ((int) call(notifyIcon, NIM_ADD, data) == 0) {
                    return false;
                }
                added = true;
            }
            var data = data(arena, NIF_INFO);
            text(data, INFO_TITLE, INFO_TITLE_CHARS, title);
            text(data, INFO, INFO_CHARS, body);
            data.set(JAVA_INT, INFO_FLAGS, NIIF_INFO);
            return (int) call(notifyIcon, NIM_MODIFY, data) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /// Takes the icon out of the notification area.
    @Override
    public void close() {
        if (!added) {
            return;
        }
        added = false;
        try (var arena = Arena.ofConfined()) {
            call(notifyIcon, NIM_DELETE, data(arena, 0));
        } catch (RuntimeException e) {
            // The shell has gone with the session; so has the icon.
        }
    }

    private MemorySegment data(Arena arena, int flags) {
        var data = arena.allocate(SIZE, 8);
        data.fill((byte) 0);
        data.set(JAVA_INT, CB_SIZE, (int) SIZE);
        data.set(JAVA_LONG, HWND, window);
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
            return handle.invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new IllegalStateException("a shell call failed", e);
        }
    }
}
