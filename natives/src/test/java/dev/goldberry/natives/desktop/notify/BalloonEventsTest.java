package dev.goldberry.natives.desktop.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What the notification icon's window procedure makes of the messages
/// Windows sends it, checked here because the window itself exists only on
/// Windows.
@DisplayName("a Windows balloon's messages")
class BalloonEventsTest {

    private final BalloonEvents events = new BalloonEvents();

    @Test
    @DisplayName("report a click on the balloon as its activation, once")
    void click() {
        events.shown(1);

        assertTrue(events.message(BalloonEvents.CALLBACK, BalloonEvents.USER_CLICK));
        assertTrue(events.message(BalloonEvents.CALLBACK, BalloonEvents.TIMEOUT), "nothing is showing now");

        assertEquals(List.of(new WindowsNotifier.Event(1, true)), events.drain());
        assertEquals(List.of(), events.drain(), "drained");
    }

    @Test
    @DisplayName("report a timeout or a hide as the balloon closing")
    void closes() {
        events.shown(1);
        events.message(BalloonEvents.CALLBACK, BalloonEvents.TIMEOUT);
        events.shown(2);
        events.message(BalloonEvents.CALLBACK, BalloonEvents.HIDE);

        assertEquals(List.of(new WindowsNotifier.Event(1, false), new WindowsNotifier.Event(2, false)), events.drain());
    }

    @Test
    @DisplayName("close a balloon that a new one replaced, and credit a click to the new one")
    void replaced() {
        events.shown(1);
        events.shown(2);
        events.message(BalloonEvents.CALLBACK, BalloonEvents.USER_CLICK);

        assertEquals(List.of(new WindowsNotifier.Event(1, false), new WindowsNotifier.Event(2, true)), events.drain());
    }

    @Test
    @DisplayName("leave every other message to the default window procedure")
    void otherMessages() {
        events.shown(1);

        assertFalse(events.message(0x0001, 0), "WM_CREATE");
        assertTrue(events.message(BalloonEvents.CALLBACK, BalloonEvents.SHOW), "the icon's, but no event");
        assertTrue(events.message(BalloonEvents.CALLBACK, 0x0202), "a click on the icon itself");
        assertEquals(List.of(), events.drain());
    }

    @Test
    @DisplayName("read the event from the low word, as the legacy form packs it")
    void lowWord() {
        events.shown(7);

        events.message(BalloonEvents.CALLBACK, 0x6B6F_0000L | BalloonEvents.USER_CLICK);

        assertEquals(List.of(new WindowsNotifier.Event(7, true)), events.drain());
    }
}
