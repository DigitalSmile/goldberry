package dev.goldberry.natives.desktop.notify;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dbus.Dbus;
import dev.goldberry.natives.desktop.dbus.PrivateBus;

/// A bus of the test's own with a notification daemon on it that records what
/// it is sent, answers `Notify` with an id, and says the user clicked it.
///
/// See [PrivateBus]: nothing on the real session is touched, so no
/// notification appears on the desktop of whoever runs the suite.
final class FakeNotificationDaemon implements AutoCloseable {

    /// The id every notification is given.
    static final int ID = 42;

    /// One `Notify` call, as its arguments arrived: app name, replaces id,
    /// icon, summary, body, actions, hints and the timeout.
    record Call(List<Object> arguments) {}

    final List<Call> calls = new CopyOnWriteArrayList<>();
    final List<List<Object>> launcherUpdates = new CopyOnWriteArrayList<>();

    private final PrivateBus bus;

    private FakeNotificationDaemon(PrivateBus bus) {
        this.bus = bus;
    }

    /// Starts a bus and the daemon on it, or null where this machine has no
    /// `dbus-daemon` or no libdbus.
    static @Nullable FakeNotificationDaemon start(Path directory) throws IOException {
        var bus = PrivateBus.start(directory);
        if (bus == null) {
            return null;
        }
        var daemon = new FakeNotificationDaemon(bus);
        if (!bus.serve(
                "org.freedesktop.Notifications",
                connection -> connection.match("type='signal',interface='com.canonical.Unity.LauncherEntry'"),
                daemon::handle)) {
            bus.close();
            return null;
        }
        return daemon;
    }

    String address() {
        return bus.address();
    }

    private void handle(Dbus.Incoming incoming) {
        if (incoming.isCall()
                && "org.freedesktop.Notifications".equals(incoming.iface())
                && "Notify".equals(incoming.member())) {
            calls.add(new Call(incoming.arguments()));
            incoming.reply(reply -> reply.uint32(ID));
            // The user clicks it at once.
            bus.service()
                    .signal(
                            "/org/freedesktop/Notifications",
                            "org.freedesktop.Notifications",
                            "ActionInvoked",
                            signal -> signal.uint32(ID).string("default"));
        } else if (!incoming.isCall()
                && "com.canonical.Unity.LauncherEntry".equals(incoming.iface())
                && "Update".equals(incoming.member())) {
            launcherUpdates.add(incoming.arguments());
        }
    }

    @Override
    public void close() {
        bus.close();
    }
}
