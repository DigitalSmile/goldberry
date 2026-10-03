package dev.goldberry.example.ui.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.platform.Capability;
import dev.goldberry.widgets.controls.button.Button;

/// The Windows screen: built the way the window builds it, then card by card
/// against a session's host, which has no window, no popups, no displays and no
/// desktop. Every card has to show that answer rather than fail on it.
class WindowsChapterTest {

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the screen holds a card for every section of the chapter")
    void cards() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("windows"))) {
            assertTrue(session.byId("screen-windows").isPresent(), "no #screen-windows");
            for (var id : List.of(
                    "windows-lifecycle",
                    "dialogs-card",
                    "windows-layers",
                    "windows-overlay",
                    "windows-popup",
                    "windows-theme",
                    "windows-capabilities",
                    "windows-notify",
                    "windows-badge",
                    "windows-fullscreen",
                    "windows-position",
                    "windows-displays",
                    "windows-attention",
                    "windows-second",
                    "windows-threads",
                    "windows-closing",
                    "windows-low-level")) {
                assertTrue(session.byId(id).isPresent(), "no #" + id);
            }
        }
    }

    @Test
    @DisplayName("an overlay floats over the window and is taken away again")
    void overlay() {
        try (var card = new CardSession(new OverlayCard())) {
            card.session.click("overlay-show");
            assertEquals(1, card.session.overlays().size());
            assertTrue(card.session.byId("overlay-note").isPresent(), "the note is not on the layer");

            card.session.click("overlay-remove");
            card.session.advance(Duration.ofSeconds(1));
            assertEquals(List.of(), card.session.overlays());
            assertEquals("Removed.", card.text("overlay-answer"));
        }
    }

    @Test
    @DisplayName("a popup the driver cannot open is reported as an empty answer")
    void popupEmpty() {
        try (var card = new CardSession(new PopupCard())) {
            card.session.click(PopupCard.ANCHOR);
            assertTrue(card.text("popup-answer").startsWith("Empty"), card.text("popup-answer"));
        }
    }

    @Test
    @DisplayName("a desktop that says nothing about its theme is not read as light")
    void themeSaysNothing() {
        try (var card = new CardSession(new SystemThemeCard())) {
            assertEquals("The desktop says nothing, which is not the same as light.", card.text("theme-answer"));
        }
    }

    @Test
    @DisplayName("capabilities are asked on a press and listed one per line")
    void capabilities() {
        var some = EnumSet.of(Capability.SYSTEM_THEME, Capability.NOTIFICATIONS);
        try (var card = new CardSession(new CapabilitiesCard(() -> some))) {
            assertEquals(List.of("Not asked yet."), card.texts("capabilities-list"));

            card.session.click("capabilities-ask");

            var lines = card.texts("capabilities-list");
            assertEquals(Capability.values().length, lines.size());
            assertTrue(lines.contains("yes  SYSTEM_THEME"), lines.toString());
            assertTrue(lines.contains("no   WAYLAND"), lines.toString());
            assertTrue(lines.contains("yes  NOTIFICATIONS"), lines.toString());
        }
    }

    @Test
    @DisplayName("a notification the desktop does not take is an ordinary answer")
    void notificationRefused() {
        try (var card = new CardSession(new NotifyCard())) {
            card.session.click("notify-send");
            assertTrue(card.text("notify-answer").startsWith("The desktop did not take it"));
        }
    }

    @Test
    @DisplayName("a badge nobody shows says the desktop was not told")
    void badgeRefused() {
        try (var card = new CardSession(new BadgeCard())) {
            card.session.click("badge-add");
            assertEquals("badge(1) → the desktop was not told", card.text("badge-answer"));
        }
    }

    @Test
    @DisplayName("fullscreen is not offered where there is no window")
    void fullscreenNotOffered() {
        try (var card = new CardSession(new FullscreenCard())) {
            var toggle = (Button)
                    card.session.byId("fullscreen-toggle").orElseThrow().widget();
            assertTrue(toggle.disabled(), "the toggle is offered with nothing to make fullscreen");
            assertTrue(card.text("fullscreen-answer").startsWith("canFullscreen() is false"));
        }
    }

    @Test
    @DisplayName("the window's place, its displays and its attention say there is no window")
    void noWindow() {
        try (var position = new CardSession(new PositionCard());
                var displays = new CardSession(new DisplaysCard());
                var attention = new CardSession(new AttentionCard())) {
            position.session.click("position-read");
            assertEquals(List.of(HostWindow.NONE), position.texts("position-lines"));

            displays.session.click("displays-list");
            assertEquals(List.of("None: this host has no desktop under it."), displays.texts("displays-lines"));

            attention.session.click("attention-ask");
            assertEquals(HostWindow.NONE, attention.text("attention-answer"));
        }
    }

    @Test
    @DisplayName("a second window this host cannot open is reported as empty")
    void secondWindowEmpty() {
        try (var card = new CardSession(new SecondWindowCard())) {
            card.session.click("second-open");
            assertTrue(card.text("second-answer").startsWith("Empty"), card.text("second-answer"));
            assertTrue(card.session.byId("second-open").isPresent(), "the card offers to raise a window it lacks");
        }
    }

    @Test
    @DisplayName("work is not started when there is no UI thread to come back to")
    void threadsWithoutLoop() {
        try (var card = new CardSession(new ThreadsCard())) {
            card.session.click("threads-start");
            assertTrue(card.text("threads-answer").startsWith("No frame loop"), card.text("threads-answer"));
        }
    }

    @Test
    @DisplayName("the work itself counts on whatever thread runs it")
    void countsPrimes() {
        var counted = ThreadsCard.count(100);
        assertEquals(25, counted.primes());
    }
}
