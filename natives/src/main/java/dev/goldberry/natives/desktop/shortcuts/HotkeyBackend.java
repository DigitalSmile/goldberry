package dev.goldberry.natives.desktop.shortcuts;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

/// One way of asking the desktop for shortcuts that work while the
/// application is not focused.
///
/// A backend holds one set of hotkeys at a time: each [#bind] replaces what
/// the one before it bound, releasing anything held first.
public interface HotkeyBackend extends AutoCloseable {

    /// The backend this desktop has, or empty where there is none this build
    /// can reach. Says nothing about whether the desktop grants anything,
    /// which only binding can tell.
    ///
    /// On Linux and the BSDs a Wayland session asks the portal and an X11
    /// session grabs keys on the root window. An X11 grab inside a Wayland
    /// session would see keys only while one of the application's own X11
    /// windows had focus, which is no global shortcut at all, so a Wayland
    /// session never falls back to one.
    ///
    /// @param applicationId the application's desktop entry id, which the
    ///                      portal shows the user and keys its settings by;
    ///                      null where it is not known
    static Optional<HotkeyBackend> forThisPlatform(@Nullable String applicationId) {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WindowsHotkeys.open().map(HotkeyBackend.class::cast);
        }
        if (os.contains("mac")) {
            return CarbonHotkeys.open().map(HotkeyBackend.class::cast);
        }
        if (os.contains("linux") || os.contains("bsd")) {
            if (isWayland(System.getenv("WAYLAND_DISPLAY"), System.getenv("XDG_SESSION_TYPE"))) {
                return PortalHotkeys.session(applicationId).map(HotkeyBackend.class::cast);
            }
            var display = System.getenv("DISPLAY");
            if (display != null && !display.isBlank()) {
                return XlibHotkeys.open(display).map(HotkeyBackend.class::cast);
            }
        }
        return Optional.empty();
    }

    /// Whether the session is Wayland's, from what a session sets.
    static boolean isWayland(@Nullable String waylandDisplay, @Nullable String sessionType) {
        return (waylandDisplay != null && !waylandDisplay.isBlank())
                || (sessionType != null && sessionType.equalsIgnoreCase("wayland"));
    }

    /// What this backend is called, for a log line or a settings page.
    String name();

    /// Whether [#bind] must be called on the process's main thread, which is
    /// macOS's rule for Carbon's event target. Every other backend has a
    /// thread of its own and takes calls from any thread.
    boolean bindsOnMainThread();

    /// Asks for `hotkeys`, in place of whatever was bound before.
    ///
    /// The answer comes once the desktop has given it, which on Wayland is
    /// after the user has answered a dialog: one outcome per hotkey, in the
    /// order given. `listener` then hears the granted ones, on the backend's
    /// thread. An empty list unbinds everything.
    CompletableFuture<List<HotkeyOutcome>> bind(List<Hotkey> hotkeys, HotkeyListener listener);

    /// Unbinds everything and lets go of the desktop. Anything held is
    /// released first.
    @Override
    void close();

    /// Every hotkey refused for one reason, for a backend that cannot ask.
    static List<HotkeyOutcome> refuseAll(List<Hotkey> hotkeys, HotkeyOutcome.Refusal reason, String detail) {
        return hotkeys.stream()
                .<HotkeyOutcome>map(hotkey -> new HotkeyOutcome.Refused(hotkey.id(), reason, detail))
                .toList();
    }
}
