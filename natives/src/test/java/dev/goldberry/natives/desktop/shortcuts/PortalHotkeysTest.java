package dev.goldberry.natives.desktop.shortcuts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativePlatform;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Bound;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Refusal;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome.Refused;

/// Shortcuts asked of the GlobalShortcuts portal, granted, pressed and
/// released.
///
/// Against a portal of the test's own on a bus of its own — see [FakePortal] —
/// so nothing on the real desktop is asked. Skipped where there is no
/// `dbus-daemon` or no libdbus.
@DisplayName("global shortcuts through the portal")
@Timeout(60)
class PortalHotkeysTest {

    private static final Hotkey TALK = new Hotkey("talk", "Push to talk", HotkeyKey.F9, 0);
    private static final Hotkey MUTE = new Hotkey("mute", "Mute", HotkeyKey.M, Hotkey.CTRL | Hotkey.ALT);

    @TempDir
    Path directory;

    private FakePortal portal;
    private PortalHotkeys backend;
    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();
    private final HotkeyListener listener = new HotkeyListener() {
        @Override
        public void pressed(String id) {
            heard.add("pressed " + id);
        }

        @Override
        public void released(String id) {
            heard.add("released " + id);
        }
    };

    @BeforeEach
    void start() throws Exception {
        assumeTrue(NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX, "D-Bus is Linux's");
        portal = FakePortal.start(directory);
        assumeTrue(portal != null, "no dbus-daemon or no libdbus here");
    }

    private PortalHotkeys open(String applicationId) {
        backend = PortalHotkeys.at(portal.address(), applicationId).orElseThrow();
        return backend;
    }

    @AfterEach
    void stop() {
        if (backend != null) {
            backend.close();
        }
        if (portal != null) {
            portal.close();
        }
    }

    private String next() throws InterruptedException {
        var event = heard.poll(10, TimeUnit.SECONDS);
        return event == null ? "nothing" : event;
    }

    @Test
    @DisplayName("registers the application's id, then reads the interface's version")
    void registers() {
        var shortcuts = open("dev.tessera.Tessera");

        assertEquals(List.of("dev.tessera.Tessera"), portal.registered);
        assertEquals(1, shortcuts.version());
    }

    @Test
    @DisplayName("asks for each shortcut with its description and preferred trigger, and hears them granted")
    void grants() throws Exception {
        var outcomes = open("app").bind(List.of(TALK, MUTE), listener).get(10, TimeUnit.SECONDS);

        assertEquals(List.of(new Bound("talk", "F9"), new Bound("mute", "CTRL+ALT+m")), outcomes);
        var asked = portal.binds.getFirst();
        assertEquals("Push to talk", asked.get("talk").get("description"));
        assertEquals("F9", asked.get("talk").get("preferred_trigger"));
        assertEquals("CTRL+ALT+m", asked.get("mute").get("preferred_trigger"));
    }

    @Test
    @DisplayName("reports the trigger the user chose rather than the one asked for")
    void userTrigger() throws Exception {
        portal.chosen.put("talk", "Super+T");

        var outcomes = open("app").bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);

        assertEquals(List.of(new Bound("talk", "Super+T")), outcomes);
    }

    @Test
    @DisplayName("a shortcut the user left out is denied, and the rest are granted")
    void partial() throws Exception {
        portal.granted = Set.of("mute");

        var outcomes = open("app").bind(List.of(TALK, MUTE), listener).get(10, TimeUnit.SECONDS);

        assertEquals(Refusal.DENIED, ((Refused) outcomes.get(0)).reason());
        assertEquals(new Bound("mute", "CTRL+ALT+m"), outcomes.get(1));
    }

    @Test
    @DisplayName("a cancelled dialog denies every shortcut")
    void cancelled() throws Exception {
        portal.answer = FakePortal.Answer.CANCEL;

        var outcomes = open("app").bind(List.of(TALK, MUTE), listener).get(10, TimeUnit.SECONDS);

        assertEquals(
                List.of(Refusal.DENIED, Refusal.DENIED),
                outcomes.stream().map(o -> ((Refused) o).reason()).toList());
    }

    @Test
    @DisplayName("a portal without GlobalShortcuts refuses every shortcut as unsupported")
    void unsupported() throws Exception {
        portal.hasShortcuts = false;

        var outcomes = open("app").bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);

        assertEquals(Refusal.UNSUPPORTED, ((Refused) outcomes.getFirst()).reason());
        assertEquals(List.of(), portal.sessionsCreated);
    }

    @Test
    @DisplayName("Activated is the press and Deactivated the release, and a repeated Activated is one press")
    void pressAndRelease() throws Exception {
        open("app").bind(List.of(TALK, MUTE), listener).get(10, TimeUnit.SECONDS);

        portal.press("talk");
        portal.press("talk");
        portal.release("talk");
        portal.release("talk");
        portal.press("mute");

        assertEquals("pressed talk", next());
        assertEquals("released talk", next());
        assertEquals("pressed mute", next());
    }

    @Test
    @DisplayName("a second bind closes the first session, releasing what was held")
    void rebinds() throws Exception {
        var shortcuts = open("app");
        shortcuts.bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);
        portal.press("talk");
        assertEquals("pressed talk", next());

        var outcomes = shortcuts.bind(List.of(MUTE), listener).get(10, TimeUnit.SECONDS);

        assertEquals("released talk", next());
        assertEquals(List.of(new Bound("mute", "CTRL+ALT+m")), outcomes);
        assertEquals(portal.sessionsCreated.subList(0, 1), portal.sessionsClosed);
        assertEquals(2, portal.sessionsCreated.size());
    }

    @Test
    @DisplayName("an empty bind closes the session and asks for nothing")
    void unbinds() throws Exception {
        var shortcuts = open("app");
        shortcuts.bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);

        assertEquals(List.of(), shortcuts.bind(List.of(), listener).get(10, TimeUnit.SECONDS));

        assertEquals(portal.sessionsCreated, portal.sessionsClosed);
    }

    @Test
    @DisplayName("closing releases what is held and closes the session")
    void closes() throws Exception {
        var shortcuts = open("app");
        shortcuts.bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);
        portal.press("talk");
        assertEquals("pressed talk", next());

        shortcuts.close();

        assertEquals("released talk", next());
        assertEquals(portal.sessionsCreated, portal.sessionsClosed);
        var after = shortcuts.bind(List.of(TALK), listener).get(10, TimeUnit.SECONDS);
        assertEquals(Refusal.UNSUPPORTED, ((Refused) after.getFirst()).reason());
        assertNull(heard.poll());
    }
}
