package dev.goldberry.natives.desktop.dbus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/// A `dbus-daemon` of a test's own, from a minimal configuration, so that
/// nothing on the real session is touched, and a service on it served by a
/// thread of its own: a client's call blocks until it is answered.
public final class PrivateBus implements AutoCloseable {

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

    private final Process bus;
    private final String address;
    private @Nullable Dbus service;
    private @Nullable Thread thread;
    private volatile boolean running = true;

    private PrivateBus(Process bus, String address) {
        this.bus = bus;
        this.address = address;
    }

    /// Starts a bus with its socket in `directory`, or null where this machine
    /// has no `dbus-daemon` or no libdbus.
    public static @Nullable PrivateBus start(Path directory) throws IOException {
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
        return new PrivateBus(process, address.trim());
    }

    public String address() {
        return address;
    }

    /// A client connection to this bus.
    public Dbus connect() {
        return Dbus.at(address).orElseThrow(() -> new IllegalStateException("cannot connect to " + address));
    }

    /// Takes `name` on this bus and serves it on a thread of its own: each
    /// message that arrives goes to `handler`, and `setUp` runs on the new
    /// connection first (a match rule, say). False when the name was taken.
    public boolean serve(String name, Consumer<Dbus> setUp, Consumer<Dbus.Incoming> handler) {
        var connection = connect();
        if (!connection.requestName(name)) {
            connection.close();
            return false;
        }
        setUp.accept(connection);
        service = connection;
        thread = Thread.ofPlatform().daemon().name("private-bus " + name).start(() -> {
            while (running) {
                connection.receive(50, handler);
            }
        });
        return true;
    }

    /// The serving connection, for a service that emits a signal of its own.
    /// Called from the handler, which is on the serving thread.
    public Dbus service() {
        if (service == null) {
            throw new IllegalStateException("nothing is served");
        }
        return service;
    }

    @Override
    public void close() {
        running = false;
        try {
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            }
            if (service != null) {
                service.close();
            }
            bus.destroy();
            bus.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            bus.destroyForcibly();
        }
    }
}
