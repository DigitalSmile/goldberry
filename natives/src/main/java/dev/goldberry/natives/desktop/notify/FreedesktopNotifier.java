package dev.goldberry.natives.desktop.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// Desktop notifications and a launcher badge on Linux, over D-Bus.
///
/// ```java
/// var notifier = FreedesktopNotifier.connect().orElseThrow();
/// long id = notifier.notify("Deploy Orc", "Gate waiting", "prod-eu needs an approval", null, "deploy-orc", true);
/// ```
///
/// The notification is the freedesktop specification's
/// `org.freedesktop.Notifications.Notify`, which GNOME Shell, KDE Plasma,
/// dunst, mako and every other notification daemon serve. The badge is the
/// `com.canonical.Unity.LauncherEntry` signal, which Ubuntu's dock, Dash to
/// Dock, Plank and KDE's task manager read; a dock that does not listen simply
/// shows nothing.
///
/// ## Clicks come back as signals
///
/// A notification that offers the `default` action is answered, when the user
/// clicks it, by an `ActionInvoked` signal some time later. Nothing waits for
/// it: [#poll()] reads whatever has arrived without blocking, and the caller
/// asks again while it still has notifications that could be clicked.
///
/// libdbus is loaded at run time and is not a build dependency, the way the
/// settings portal reads reduced motion.
public final class FreedesktopNotifier implements AutoCloseable {

    private static final String SERVICE = "org.freedesktop.Notifications";
    private static final String PATH = "/org/freedesktop/Notifications";
    private static final String LAUNCHER_ENTRY = "com.canonical.Unity.LauncherEntry";
    private static final String LAUNCHER_PATH = "/dev/goldberry/LauncherEntry";

    /// The action key the specification reserves for clicking the notification
    /// itself rather than a button on it.
    private static final String DEFAULT_ACTION = "default";

    /// How long a notification may take to be accepted, in milliseconds. The
    /// call blocks the UI thread, and a daemon that answers at all answers in
    /// a few.
    private static final int TIMEOUT_MILLIS = 1000;

    private final Dbus bus;

    private FreedesktopNotifier(Dbus bus) {
        this.bus = bus;
        bus.match("type='signal',interface='" + SERVICE + "'");
    }

    /// Whether this process can speak D-Bus at all: libdbus is loadable.
    public static boolean isAvailable() {
        return Dbus.isAvailable();
    }

    /// A connection to the session bus, or empty where there is no libdbus or
    /// no session bus — a container, an SSH session, a CI runner.
    public static Optional<FreedesktopNotifier> connect() {
        return Dbus.session().map(FreedesktopNotifier::new);
    }

    /// A connection to the bus at `address`, for a test that runs a bus of its
    /// own.
    static Optional<FreedesktopNotifier> connect(String address) {
        return Dbus.at(address).map(FreedesktopNotifier::new);
    }

    /// Shows a notification.
    ///
    /// @param appName      the application's name, which the daemon shows
    /// @param title        the summary line
    /// @param body         the text under it; the daemon may render a little
    ///                     markup, and an application that does not mean any
    ///                     escapes `<` and `&`
    /// @param icon         a `file://` URI or a themed icon name, or null
    /// @param desktopEntry the application's desktop entry id without
    ///                     `.desktop`, which a daemon uses to group and attribute
    ///                     the notification, or null
    /// @param activatable  whether clicking it should be reported by [#poll()]
    /// @return the id the daemon gave it, or 0 when nothing was shown — no
    ///         daemon on the bus, or one that refused
    public long notify(
            String appName,
            String title,
            String body,
            @Nullable String icon,
            @Nullable String desktopEntry,
            boolean activatable) {
        var id = new long[] {0};
        try {
            var answered = bus.call(
                    SERVICE,
                    PATH,
                    SERVICE,
                    "Notify",
                    arguments -> {
                        arguments
                                .string(appName)
                                .uint32(0)
                                .string(icon == null ? "" : icon)
                                .string(title)
                                .string(body);
                        arguments.array("s", actions -> {
                            if (activatable) {
                                actions.string(DEFAULT_ACTION).string("Open");
                            }
                        });
                        arguments.array("{sv}", hints -> {
                            if (desktopEntry != null) {
                                hints.entry("desktop-entry", "s", value -> value.string(desktopEntry));
                            }
                        });
                        arguments.int32(-1);
                    },
                    TIMEOUT_MILLIS,
                    reply -> id[0] = reply.firstUint32());
            return answered && id[0] > 0 ? id[0] : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /// What happened to a notification since the last [#poll()].
    ///
    /// @param id        the id [#notify] answered with
    /// @param activated true when the user clicked it, false when it closed —
    ///                  expired, dismissed, or replaced
    public record Event(long id, boolean activated) {}

    /// Reads what has arrived, without waiting.
    public List<Event> poll() {
        var events = new ArrayList<Event>();
        try {
            for (var incoming : bus.drain(SERVICE)) {
                var arguments = incoming.arguments();
                if (arguments.size() < 2 || !(arguments.getFirst() instanceof Integer raw)) {
                    continue;
                }
                var id = Integer.toUnsignedLong(raw);
                switch (incoming.member()) {
                    case "ActionInvoked" -> {
                        if (DEFAULT_ACTION.equals(arguments.get(1))) {
                            events.add(new Event(id, true));
                        }
                    }
                    case "NotificationClosed" -> events.add(new Event(id, false));
                    default -> {
                        // ActivationToken and whatever the specification adds.
                    }
                }
            }
        } catch (RuntimeException e) {
            // A bus that went away has nothing more to say.
        }
        return events;
    }

    /// Sets the number on the application's launcher icon, or takes it away
    /// for 0.
    ///
    /// A signal rather than a call, so it is never refused and never
    /// acknowledged: true means it was sent, and whether a dock shows it is the
    /// dock's business.
    ///
    /// @param desktopEntry the application's desktop entry id without `.desktop`,
    ///                     which is how a dock knows whose icon it is
    /// @param count        the number, or 0 for none
    public boolean badge(String desktopEntry, long count) {
        try {
            return bus.signal(LAUNCHER_PATH, LAUNCHER_ENTRY, "Update", arguments -> {
                arguments.string("application://" + desktopEntry + ".desktop");
                arguments.array("{sv}", properties -> {
                    properties.entry("count", "x", value -> value.int64(Math.max(0, count)));
                    properties.entry("count-visible", "b", value -> value.bool(count > 0));
                });
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        bus.close();
    }
}
