package dev.goldberry.render.desktop.notify;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.macos.MacNotifier;
import dev.goldberry.natives.desktop.macos.ObjC;
import dev.goldberry.natives.desktop.notify.FreedesktopNotifier;
import dev.goldberry.natives.desktop.notify.WindowsNotifier;

/// [BackendNotifier] on the desktop the process is running on.
///
/// | | notification | badge | click |
/// |---|---|---|---|
/// | Linux | `org.freedesktop.Notifications` over D-Bus | Unity `LauncherEntry`, a number | yes, by [#poll()] |
/// | macOS | `UNUserNotificationCenter`, in an app bundle only | the dock tile, any text | yes |
/// | Windows | the notification area's balloon, shown as a toast | none | no |
///
/// A Linux launcher finds the application's icon by its desktop entry id,
/// which only the application knows: [#DESKTOP_ID_PROPERTY], or the
/// `GIO_LAUNCHED_DESKTOP_FILE` a desktop sets when it starts an application
/// from its entry.
///
/// UI-thread confined.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
public final class PlatformNotifier implements BackendNotifier {

    /// The application's desktop entry id, without `.desktop` — what a Linux
    /// dock knows its icon by.
    public static final String DESKTOP_ID_PROPERTY = "goldberry.desktop.id";

    private static final Logger LOG = Logs.of(PlatformNotifier.class);

    private final Platform platform;
    private final LongSupplier window;

    private @Nullable FreedesktopNotifier linux;
    private boolean linuxTried;
    private @Nullable WindowsNotifier windows;
    private boolean saidNoBundle;

    /// What to run for each Linux notification that can still be clicked, by
    /// the daemon's id; and on macOS by identifier.
    private final Map<Long, Runnable> linuxPending = new HashMap<>();

    private final Map<String, Runnable> macPending = new HashMap<>();

    /// Which desktop this is. A parameter of the constructor so that a test can
    /// ask about the others.
    public enum Platform {
        LINUX,
        MACOS,
        WINDOWS,
        OTHER;

        /// The platform `os.name` names.
        public static Platform of(String osName) {
            var name = osName.toLowerCase(Locale.ROOT);
            if (name.contains("win")) {
                return WINDOWS;
            }
            if (name.contains("mac") || name.contains("darwin")) {
                return MACOS;
            }
            if (name.contains("linux") || name.contains("bsd")) {
                return LINUX;
            }
            return OTHER;
        }

        /// The platform this process runs on.
        public static Platform current() {
            return of(System.getProperty("os.name", ""));
        }
    }

    /// @param window the `HWND` a Windows notification-area icon belongs to,
    ///        asked when the first notification is posted; 0 for none
    public PlatformNotifier(LongSupplier window) {
        this(Platform.current(), window);
    }

    PlatformNotifier(Platform platform, LongSupplier window) {
        this.platform = platform;
        this.window = window;
    }

    /// Whether this process could post a notification at all: the library
    /// behind the platform's service is there. Says nothing about whether a
    /// service is running, which only posting can tell.
    public static boolean isAvailable() {
        return switch (Platform.current()) {
            case LINUX -> FreedesktopNotifier.isAvailable();
            case MACOS -> ObjC.get().isPresent();
            case WINDOWS -> true;
            case OTHER -> false;
        };
    }

    @Override
    public boolean post(String application, Notification notification) {
        return switch (platform) {
            case LINUX -> postLinux(application, notification);
            case MACOS -> postMac(notification);
            case WINDOWS -> postWindows(application, notification);
            case OTHER -> false;
        };
    }

    @Override
    public boolean badge(@Nullable String label) {
        return switch (platform) {
            case LINUX -> badgeLinux(label);
            case MACOS -> MacNotifier.get().map(mac -> mac.badge(label)).orElse(false);
            case WINDOWS, OTHER -> false;
        };
    }

    @Override
    public boolean poll() {
        if (platform != Platform.LINUX || linux == null) {
            // macOS delivers a click through the delegate, from SDL's own pump; and
            // Windows does not deliver one. Nothing to ask either.
            return false;
        }
        for (var event : linux.poll()) {
            var action = linuxPending.remove(event.id());
            if (action != null && event.activated()) {
                run(action);
            }
        }
        return !linuxPending.isEmpty();
    }

    @Override
    public void close() {
        if (linux != null) {
            linux.close();
            linux = null;
        }
        if (windows != null) {
            windows.close();
            windows = null;
        }
        linuxPending.clear();
        macPending.clear();
    }

    private boolean postLinux(String application, Notification notification) {
        var bus = linux();
        if (bus == null) {
            return false;
        }
        var icon = notification.icon() == null ? null : iconUri(notification.icon());
        var id = bus.notify(
                application,
                notification.title(),
                notification.escapedBody(),
                icon,
                desktopId(),
                notification.onActivate() != null);
        if (id == 0) {
            LOG.debug("no notification daemon took \"{}\"", notification.title());
            return false;
        }
        if (notification.onActivate() != null) {
            linuxPending.put(id, notification.onActivate());
        }
        return true;
    }

    private boolean badgeLinux(@Nullable String label) {
        var id = desktopId();
        if (id == null) {
            LOG.debug(
                    "no badge: a Linux launcher finds an application's icon by its desktop entry, and"
                            + " -D{} names none",
                    DESKTOP_ID_PROPERTY);
            return false;
        }
        long count;
        try {
            count = label == null || label.isBlank() ? 0 : Long.parseLong(label.strip());
        } catch (NumberFormatException e) {
            LOG.debug("no badge: a Linux launcher shows a number, and \"{}\" is not one", label);
            return false;
        }
        var bus = linux();
        return bus != null && bus.badge(id, count);
    }

    private boolean postMac(Notification notification) {
        var mac = MacNotifier.get();
        if (mac.isEmpty()) {
            return false;
        }
        if (!mac.get().hasBundle()) {
            if (!saidNoBundle) {
                saidNoBundle = true;
                LOG.info("macOS shows notifications only for an application bundle, and this process has no"
                        + " bundle identifier; package the application as an .app to post them");
            }
            return false;
        }
        mac.get().onActivated(identifier -> {
            var action = macPending.remove(identifier);
            if (action != null) {
                run(action);
            }
        });
        var identifier = mac.get().post(notification.title(), notification.body());
        if (identifier == null) {
            return false;
        }
        if (notification.onActivate() != null) {
            macPending.put(identifier, notification.onActivate());
        }
        return true;
    }

    private boolean postWindows(String application, Notification notification) {
        if (windows == null) {
            windows = WindowsNotifier.forWindow(window.getAsLong()).orElse(null);
        }
        return windows != null && windows.notify(application, notification.title(), notification.body());
    }

    private @Nullable FreedesktopNotifier linux() {
        if (!linuxTried) {
            linuxTried = true;
            linux = FreedesktopNotifier.connect().orElse(null);
            if (linux == null) {
                LOG.debug("no session bus, so no notifications");
            }
        }
        return linux;
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOG.warn("a notification's action failed", e);
        }
    }

    /// The application's desktop entry id: the property, or the entry the
    /// desktop started it from.
    static @Nullable String desktopId() {
        return desktopId(System.getProperty(DESKTOP_ID_PROPERTY), System.getenv("GIO_LAUNCHED_DESKTOP_FILE"));
    }

    static @Nullable String desktopId(@Nullable String property, @Nullable String launchedFrom) {
        if (property != null && !property.isBlank()) {
            return strip(property.strip());
        }
        if (launchedFrom != null && !launchedFrom.isBlank()) {
            var name = Optional.ofNullable(Path.of(launchedFrom).getFileName())
                    .map(Path::toString)
                    .orElse("");
            return name.isEmpty() ? null : strip(name);
        }
        return null;
    }

    private static String strip(String id) {
        return id.endsWith(".desktop") ? id.substring(0, id.length() - ".desktop".length()) : id;
    }

    private static String iconUri(Path icon) {
        return icon.toAbsolutePath().toUri().toString();
    }
}
