package io.github.digitalsmile.goldberry.render.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.web.WebViewEngine;

/// What an open web page costs the event loop — ADR-0441.
///
/// A page's engine runs on a loop of its own that nothing else drives, and the
/// loop can be parked in `pumpEvents` for its one-second heartbeat. So while a
/// page is open the wait is capped, and the thing worth guarding is the *other*
/// half: that an application which never opens a page pays nothing, neither in
/// wakeups nor in a loaded GTK.
@DisplayName("the event loop and a web page")
class EventLoopWebViewTest {

    private HeadlessBackend backend;
    private EventLoop loop;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        loop = new EventLoop(backend);
    }

    @AfterEach
    void tearDown() {
        loop.close();
        backend.close();
    }

    @Test
    @DisplayName("no page is open in a suite that never opens one")
    void noPageIsOpen() {
        // The precondition every assertion below rests on, asserted rather than
        // assumed: a test run that had somehow left a page open would make the
        // timeouts here look wrong for a reason that had nothing to do with them.
        assertFalse(WebViewEngine.hasOpenPages());
    }

    @Test
    @DisplayName("the heartbeat is untouched while no page is open")
    void theHeartbeatIsUnchanged() {
        // The regression that would hurt everyone: capping the wait
        // unconditionally would wake every Goldberry application 125 times a
        // second for a feature it does not use.
        assertEquals(Duration.ofSeconds(1), loop.nextTimeout());
    }

    @Test
    @DisplayName("a timer still shortens the wait, and is not lengthened to the cap")
    void aTimerStillWins() {
        var timed = new TestClock().loopOver(backend);
        try {
            timed.ui().execute(() -> {});
            timed.after(Duration.ofMillis(3), () -> {});

            // Three milliseconds, not the eight a page would ask for and not the
            // second the heartbeat is: the cap is a ceiling on the wait, never a
            // floor.
            assertTrue(timed.nextTimeout().compareTo(Duration.ofMillis(3)) <= 0, "" + timed.nextTimeout());
        } finally {
            timed.close();
        }
    }

    @Test
    @DisplayName("pumping with no page open touches nothing and loads nothing")
    void pumpingIsFreeWhenIdle() {
        // Called once per loop iteration in every application that has ever run.
        // It must not so much as look at the library: WebviewLibrary is what maps
        // GTK and WebKit into the process, and the counter is read first
        // precisely so that it is not.
        for (var i = 0; i < 1000; i++) {
            WebViewEngine.pump();
        }

        assertFalse(WebViewEngine.hasOpenPages());
    }
}
