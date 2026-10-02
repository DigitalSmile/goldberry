package dev.goldberry.render.desktop.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Posting a notification, and looking for its click only while one could
/// come.
@DisplayName("a notification")
class NotificationCenterTest {

    /// A notifier that records, and reports a click on the third look.
    private static final class Recording implements BackendNotifier {

        final List<String> posted = new ArrayList<>();

        @Nullable
        Runnable pending;

        int polls;
        boolean accept = true;

        @Override
        public boolean post(String application, Notification notification) {
            posted.add(application + ": " + notification.title());
            pending = notification.onActivate();
            return accept;
        }

        @Override
        public boolean badge(@Nullable String label) {
            posted.add("badge " + label);
            return true;
        }

        @Override
        public boolean poll() {
            polls++;
            if (polls == 3 && pending != null) {
                pending.run();
                pending = null;
            }
            return pending != null;
        }
    }

    /// The loop's timer, run by hand.
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();

    private void runTimers() {
        while (!timers.isEmpty()) {
            timers.removeFirst().run();
        }
    }

    @Test
    @DisplayName("is posted under the application's name")
    void posts() {
        var notifier = new Recording();
        var center = new NotificationCenter(() -> notifier, (delay, action) -> timers.add(action));

        assertTrue(center.post("Deploy Orc", Notification.of("Gate waiting", "prod-eu")));

        assertEquals(List.of("Deploy Orc: Gate waiting"), notifier.posted);
        assertTrue(timers.isEmpty(), "a notification with no action is never looked after");
    }

    @Test
    @DisplayName("is looked after until its click arrives, and then no longer")
    void looksForTheClick() {
        var notifier = new Recording();
        var center = new NotificationCenter(() -> notifier, (delay, action) -> {
            delays.add(delay);
            timers.add(action);
        });
        var clicked = new ArrayList<String>();

        center.post("App", Notification.of("Gate", "body").onActivate(() -> clicked.add("gate")));
        runTimers();

        assertEquals(List.of("gate"), clicked);
        assertEquals(3, notifier.polls);
        assertTrue(delays.stream().allMatch(NotificationCenter.POLL_INTERVAL::equals));
    }

    @Test
    @DisplayName("is looked after once, however many are waiting")
    void oneLookAtATime() {
        var notifier = new Recording();
        var center = new NotificationCenter(() -> notifier, (delay, action) -> timers.add(action));

        center.post("App", Notification.of("One", "").onActivate(() -> {}));
        center.post("App", Notification.of("Two", "").onActivate(() -> {}));

        assertEquals(1, timers.size());
    }

    @Test
    @DisplayName("that the desktop refused is not looked after")
    void refused() {
        var notifier = new Recording();
        notifier.accept = false;
        var center = new NotificationCenter(() -> notifier, (delay, action) -> timers.add(action));

        assertFalse(center.post("App", Notification.of("Gate", "").onActivate(() -> {})));
        assertTrue(timers.isEmpty());
    }

    @Test
    @DisplayName("runs the host's repaint after its own action")
    void andThen() {
        var ran = new ArrayList<String>();
        var notification =
                Notification.of("Gate", "").onActivate(() -> ran.add("open")).andThen(() -> ran.add("paint"));

        notification.onActivate().run();

        assertEquals(List.of("open", "paint"), ran);
        assertNull(Notification.of("Gate", "").andThen(() -> ran.add("x")).onActivate());
    }

    @Test
    @DisplayName("needs a title, and escapes what a Linux daemon would read as markup")
    void titleAndMarkup() {
        assertThrows(IllegalArgumentException.class, () -> Notification.of(" ", "body"));
        assertEquals("a &lt;b&gt; &amp; c", Notification.of("t", "a <b> & c").escapedBody());
        assertEquals(
                Path.of("/tmp/icon.png"),
                Notification.of("t", "").icon(Path.of("/tmp/icon.png")).icon());
    }

    @Test
    @DisplayName("shows nothing on the headless backend")
    void none() {
        assertFalse(BackendNotifier.NONE.post("App", Notification.of("Gate", "")));
        assertFalse(BackendNotifier.NONE.badge("3"));
        assertFalse(BackendNotifier.NONE.poll());
    }

    @Test
    @DisplayName("finds the application's desktop entry from the property, or the entry it was started from")
    void desktopEntry() {
        assertEquals("deploy-orc", PlatformNotifier.desktopId("deploy-orc", null));
        assertEquals("deploy-orc", PlatformNotifier.desktopId("deploy-orc.desktop", "/x/other.desktop"));
        assertEquals("ru.mws.Orc", PlatformNotifier.desktopId(null, "/usr/share/applications/ru.mws.Orc.desktop"));
        assertNull(PlatformNotifier.desktopId(null, null));
        assertNull(PlatformNotifier.desktopId(" ", ""));
    }

    @Test
    @DisplayName("tells the platforms apart by name")
    void platforms() {
        assertEquals(PlatformNotifier.Platform.LINUX, PlatformNotifier.Platform.of("Linux"));
        assertEquals(PlatformNotifier.Platform.MACOS, PlatformNotifier.Platform.of("Mac OS X"));
        assertEquals(PlatformNotifier.Platform.WINDOWS, PlatformNotifier.Platform.of("Windows 11"));
        assertFalse(
                new PlatformNotifier(PlatformNotifier.Platform.OTHER, () -> 0).post("App", Notification.of("t", "")));
        assertFalse(new PlatformNotifier(PlatformNotifier.Platform.WINDOWS, () -> 0).badge("3"));
    }
}
