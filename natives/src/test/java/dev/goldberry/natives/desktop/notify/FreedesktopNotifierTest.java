package dev.goldberry.natives.desktop.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativePlatform;

/// A notification goes out as the freedesktop specification says, and the
/// click comes back.
///
/// Against a bus and a daemon of the test's own — see [FakeNotificationDaemon]
/// — so nothing appears on the desktop of whoever runs the suite. Skipped
/// where there is no `dbus-daemon` or no libdbus.
@DisplayName("a freedesktop notification")
class FreedesktopNotifierTest {

    @TempDir
    Path directory;

    private FakeNotificationDaemon daemon;
    private FreedesktopNotifier notifier;

    @BeforeEach
    void start() throws Exception {
        assumeTrue(NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX, "D-Bus is Linux's");
        daemon = FakeNotificationDaemon.start(directory);
        assumeTrue(daemon != null, "no dbus-daemon or no libdbus here");
        notifier = FreedesktopNotifier.connect(daemon.address()).orElseThrow();
    }

    @AfterEach
    void stop() throws Exception {
        if (notifier != null) {
            notifier.close();
        }
        if (daemon != null) {
            daemon.close();
        }
    }

    @Test
    @DisplayName("is sent as Notify, with its title, body, default action and desktop entry")
    void sendsNotify() {
        var id = notifier.notify("Deploy Orc", "Gate waiting", "prod-eu needs an approval", null, "deploy-orc", true);

        assertEquals(FakeNotificationDaemon.ID, id);
        assertEquals(1, daemon.calls.size());
        var arguments = daemon.calls.getFirst().arguments();
        assertEquals("Deploy Orc", arguments.get(0));
        assertEquals(0, arguments.get(1), "replaces nothing");
        assertEquals("", arguments.get(2), "no icon");
        assertEquals("Gate waiting", arguments.get(3));
        assertEquals("prod-eu needs an approval", arguments.get(4));
        assertEquals(new Dbus.Skipped('a'), arguments.get(5), "the actions array");
        assertEquals(new Dbus.Skipped('a'), arguments.get(6), "the hints dictionary");
        assertEquals(-1, arguments.get(7), "the daemon's own timeout");
    }

    @Test
    @DisplayName("reports the user's click as an activation of that notification")
    void reportsTheClick() throws InterruptedException {
        var id = notifier.notify("Deploy Orc", "Gate waiting", "body", null, null, true);

        var events = pollUntilSomething();

        assertTrue(events.contains(new FreedesktopNotifier.Event(id, true)), events.toString());
    }

    @Test
    @DisplayName("sets the launcher badge for the application's desktop entry")
    void badges() throws InterruptedException {
        assertTrue(notifier.badge("deploy-orc", 3));

        var deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (daemon.launcherUpdates.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertEquals(1, daemon.launcherUpdates.size());
        assertEquals(
                "application://deploy-orc.desktop",
                daemon.launcherUpdates.getFirst().getFirst());
    }

    @Test
    @DisplayName("answers 0 rather than throwing when nobody serves notifications")
    void nobodyThere() throws Exception {
        daemon.close();
        var id = notifier.notify("Deploy Orc", "Gate waiting", "body", null, null, false);
        daemon = null;

        assertEquals(0, id);
    }

    private List<FreedesktopNotifier.Event> pollUntilSomething() throws InterruptedException {
        var events = new ArrayList<FreedesktopNotifier.Event>();
        var deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (events.isEmpty() && Instant.now().isBefore(deadline)) {
            events.addAll(notifier.poll());
            Thread.sleep(10);
        }
        return events;
    }
}
