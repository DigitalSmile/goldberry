package dev.goldberry.example.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.Cursor;

/// The Input screen: built the way the window builds it, then card by card,
/// driven through the router the way a user drives it.
class InputChapterTest {

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
                        .session(scene.root("input"))) {
            assertTrue(session.byId("screen-input").isPresent(), "no #screen-input");
            for (var id : List.of(
                    "input-travel",
                    "input-kinds",
                    "input-capture",
                    "input-gestures",
                    "input-wheel",
                    "input-keys",
                    "input-accelerators",
                    "input-focus",
                    "input-composite",
                    "input-cursor",
                    "input-cursor-pictures",
                    "input-drop",
                    "input-context",
                    "input-custom")) {
                assertTrue(session.byId(id).isPresent(), "no #" + id);
            }
        }
    }

    @Test
    @DisplayName("a press is traced down through every box and back up")
    void pressTravels() {
        try (var card = new CardSession(new EventTravelCard())) {
            card.session.click("travel-inner");

            assertEquals(
                    List.of(
                            "capture  outer",
                            "capture  middle",
                            "capture  inner",
                            "target   inner",
                            "bubble   middle",
                            "bubble   outer"),
                    card.texts("travel-trace"));
        }
    }

    @Test
    @DisplayName("a box that consumes the press stops it on its way up")
    void consumeStops() {
        try (var card = new CardSession(new EventTravelCard())) {
            card.session.click("travel-consume");
            card.session.click("travel-inner");

            var trace = card.texts("travel-trace");
            assertEquals("bubble   middle  (consumed here)", trace.getLast(), trace.toString());
            assertFalse(trace.contains("bubble   outer"), trace.toString());
        }
    }

    @Test
    @DisplayName("the kinds pad counts a press, a release and a click")
    void kindsAreCounted() {
        try (var card = new CardSession(new PointerKindsCard())) {
            card.session.click("kinds-pad");

            assertEquals("PRESSED 1", card.text("kind-pressed"));
            assertEquals("RELEASED 1", card.text("kind-released"));
            assertEquals("CLICKED 1", card.text("kind-clicked"));
        }
    }

    @Test
    @DisplayName("a drag that leaves the pad is still reported to it")
    void dragOutsideIsCaptured() {
        try (var card = new CardSession(new CaptureCard())) {
            var pad = card.rect("capture-pad");
            var router = card.session.router();
            var x = pad.left() + 20;
            var y = pad.top() + 20;
            router.pointerMoved(x, y);
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
            card.session.frame();
            router.pointerMoved(pad.right() + 80, pad.bottom() + 40);
            card.session.frame();
            assertTrue(card.text("capture-readout").endsWith("outside the pad."), card.text("capture-readout"));

            router.pointerReleased(pad.right() + 80, pad.bottom() + 40, PointerEvent.Button.PRIMARY, 1);
            card.session.frame();
            assertTrue(card.text("capture-readout").contains("still heard it"), card.text("capture-readout"));
        }
    }

    @Test
    @DisplayName("a drag along the track reports the router's gesture numbers")
    void dragReportsTheGesture() {
        try (var card = new CardSession(new DragCard())) {
            var track = card.rect("drag-track");
            var router = card.session.router();
            var y = track.top() + track.height() / 2;
            router.pointerMoved(track.left() + 10, y);
            router.pointerPressed(track.left() + 10, y, PointerEvent.Button.PRIMARY, 1);
            card.session.frame();
            router.pointerMoved(track.left() + track.width() * 0.75f, y);
            card.session.frame();

            var readout = card.text("drag-readout");
            assertTrue(readout.contains("fractionX 0.75"), readout);
            assertTrue(readout.contains("dragY +0"), readout);
        }
    }

    /// The strip says `grab`, and `grabbing` while it holds the card. The second
    /// shape arrives during the drag, because the box holding the pointer is the
    /// one whose own cursor changed.
    @Test
    @DisplayName("picking the card up closes the hand mid-drag, and letting go opens it")
    void pickingUpClosesTheHand() {
        try (var card = new CardSession(new CursorPicturesCard())) {
            var strip = card.rect(CursorPicturesCard.STRIP);
            var router = card.session.router();
            var y = strip.top() + strip.height() / 2;
            var x = strip.left() + 20;
            router.pointerMoved(x, y);
            assertEquals(Cursor.GRAB, router.cursor());

            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
            card.session.frame();
            assertEquals(Cursor.GRABBING, router.cursor(), "the held strip's own cursor is followed");
            assertTrue(card.text("pick-readout").startsWith("Held"), card.text("pick-readout"));

            router.pointerMoved(strip.right() + 60, y);
            card.session.frame();
            assertEquals(Cursor.GRABBING, router.cursor(), "off the strip, still the drag's shape");

            router.pointerReleased(strip.right() + 60, y, PointerEvent.Button.PRIMARY, 1);
            card.session.frame();
            router.pointerMoved(x, y);
            assertEquals(Cursor.GRAB, router.cursor(), "back over the strip, the open hand again");
        }
    }

    @Test
    @DisplayName("the wheel over the plan reports lines and zooms it")
    void wheelIsLines() {
        try (var card = new CardSession(new WheelCard())) {
            var plan = card.rect("wheel-plan");
            card.session.wheel(plan.left() + 40, plan.top() + 40, 0, 1);

            var readout = card.text("wheel-readout");
            assertTrue(readout.startsWith("deltaY 1.00 lines"), readout);
            assertTrue(readout.contains("zoom 0.91×"), readout);
        }
    }

    @Test
    @DisplayName("the keys pad logs a key and the text it typed as two events")
    void keysAndText() {
        try (var card = new CardSession(new KeysCard())) {
            card.session.click("keys-pad");
            card.session.key(Key.A, Modifiers.NONE);
            card.session.type("a");

            assertEquals(List.of("key   A", "text  \"a\""), card.texts("keys-log"));
        }
    }

    @Test
    @DisplayName("the card's own accelerator fires while it is on screen")
    void acceleratorFires() {
        try (var card = new CardSession(new AcceleratorsCard())) {
            var own = AcceleratorsCard.OWN;
            card.session.key(own.key(), own.modifiers());
            card.session.key(own.key(), own.modifiers());

            assertTrue(card.text("accelerator-own").endsWith("Pressed 2 times."), card.text("accelerator-own"));
        }
    }

    @Test
    @DisplayName("focus moves by id, and is refused for a disabled field")
    void focusById() {
        try (var card = new CardSession(new FocusCard())) {
            card.session.click("focus-to-second");
            assertEquals("host.focus(\"focus-second\") → moved", card.text("focus-answer"));

            card.session.click("focus-to-disabled");
            assertEquals("host.focus(\"focus-disabled\") → refused", card.text("focus-answer"));
        }
    }

    @Test
    @DisplayName("the arrows move a radio group's selection, which is its one Tab stop")
    void compositeRoves() {
        try (var card = new CardSession(new CompositeCard())) {
            card.session.click("composite-bree");
            card.session.key(Key.TAB, Modifiers.of(Mod.SHIFT));
            card.session.key(Key.DOWN, Modifiers.NONE);

            assertTrue(card.text("composite-readout").startsWith("Road: north"), card.text("composite-readout"));
        }
    }

    @Test
    @DisplayName("with no window under the host, the drop card says so rather than failing")
    void dropWithoutWindow() {
        try (var card = new CardSession(new DropCard())) {
            assertTrue(card.text("drop-readout").contains("no window"), card.text("drop-readout"));
        }
    }
}
