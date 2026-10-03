package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/// A bus of the test's own with a notification daemon on it that records what
/// it is sent, answers `Notify` with an id, and says the user clicked it.
///
/// A `dbus-daemon` started from a minimal configuration, so that nothing on the
/// real session is touched — no notification appears on the desktop of
/// whoever runs the suite — and the daemon is Java over the same libdbus the
/// notifier uses, on a thread of its own: the notifier's call blocks until it
/// is answered.
final class FakeNotificationDaemon implements AutoCloseable {

    private static final String CONFIG = """
            <!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
             "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
            <busconfig>
              <type>session</type>
              <listen>unix:tmpdir=%s</listen>
              <auth>EXTERNAL</auth>
              <policy context="default">
                <allow send_destination="*" eavesdrop="true"/>
                <allow eavesdrop="true"/>
                <allow own="*"/>
              </policy>
            </busconfig>
            """;

    /// The id every notification is given.
    static final int ID = 42;

    /// One `Notify` call, as its basic arguments arrived: app name, replaces id,
    /// icon, summary, body, then the two containers and the timeout.
    record Call(List<Object> arguments) {}

    final List<Call> calls = new CopyOnWriteArrayList<>();
    final List<List<Object>> launcherUpdates = new CopyOnWriteArrayList<>();

    private final Process bus;
    private final String address;
    private final Dbus service;
    private final Thread thread;
    private volatile boolean running = true;

    private FakeNotificationDaemon(Process bus, String address, Dbus service) {
        this.bus = bus;
        this.address = address;
        this.service = service;
        this.thread =
                Thread.ofPlatform().daemon().name("fake-notification-daemon").start(this::serve);
    }

    /// Starts a bus and the daemon on it, or null where this machine has no
    /// `dbus-daemon` or no libdbus.
    static FakeNotificationDaemon start(Path directory) throws IOException, InterruptedException {
        if (!Dbus.isAvailable() || !Files.isExecutable(Path.of("/usr/bin/dbus-daemon"))) {
            return null;
        }
        var config = directory.resolve("bus.conf");
        Files.writeString(config, CONFIG.formatted(directory));
        var process = new ProcessBuilder(
                        "/usr/bin/dbus-daemon", "--config-file=" + config, "--nofork", "--print-address=1")
                .redirectErrorStream(false)
                .start();
        // `--print-address=1` writes the address once and nothing after it, so
        // the pipe is done with as soon as the line is read.
        String address;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            address = reader.readLine();
        }
        if (address == null || address.isBlank()) {
            process.destroyForcibly();
            return null;
        }
        var service = Dbus.at(address.trim()).orElse(null);
        if (service == null) {
            process.destroyForcibly();
            return null;
        }
        var lib = Dbus.library();
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            // DBUS_NAME_FLAG_DO_NOT_QUEUE: own it now or fail.
            lib.call(
                    lib.requestName,
                    service.connection(),
                    arena.allocateFrom("org.freedesktop.Notifications"),
                    4,
                    error);
            lib.errorFree(error);
        }
        service.match("type='signal',interface='com.canonical.Unity.LauncherEntry'");
        return new FakeNotificationDaemon(process, address.trim(), service);
    }

    String address() {
        return address;
    }

    private void serve() {
        var lib = Dbus.library();
        var connection = service.connection();
        while (running) {
            lib.call(lib.readWrite, connection, 50);
            while (true) {
                var message = (MemorySegment) lib.call(lib.popMessage, connection);
                if (message.address() == 0) {
                    break;
                }
                try (var arena = Arena.ofConfined()) {
                    var iface = Dbus.Library.string((MemorySegment) lib.call(lib.getInterface, message));
                    var member = Dbus.Library.string((MemorySegment) lib.call(lib.getMember, message));
                    var arguments = new Dbus.Reader(lib, arena, message).basics();
                    if ("org.freedesktop.Notifications".equals(iface) && "Notify".equals(member)) {
                        calls.add(new Call(arguments));
                        reply(lib, connection, message);
                        // The user clicks it at once.
                        service.signal(
                                "/org/freedesktop/Notifications",
                                "org.freedesktop.Notifications",
                                "ActionInvoked",
                                signal -> signal.uint32(ID).string("default"));
                    } else if ("com.canonical.Unity.LauncherEntry".equals(iface) && "Update".equals(member)) {
                        launcherUpdates.add(arguments);
                    }
                } finally {
                    lib.call(lib.messageUnref, message);
                }
            }
        }
    }

    private static void reply(Dbus.Library lib, MemorySegment connection, MemorySegment call) {
        var reply = (MemorySegment) lib.call(lib.newMethodReturn, call);
        try (var arena = Arena.ofConfined()) {
            var iterator = lib.iterator(arena);
            lib.call(lib.iterInitAppend, reply, iterator);
            lib.call(lib.iterAppendBasic, iterator, Dbus.UINT32, arena.allocateFrom(JAVA_INT, ID));
            lib.call(lib.send, connection, reply, MemorySegment.NULL);
            lib.call(lib.flush, connection);
        } finally {
            lib.call(lib.messageUnref, reply);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
            service.close();
            bus.destroy();
            bus.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            bus.destroyForcibly();
        }
    }
}
