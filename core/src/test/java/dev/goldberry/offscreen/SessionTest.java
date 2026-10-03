package dev.goldberry.offscreen;

import static dev.goldberry.offscreen.Scene.RED;
import static dev.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.tap.ModifierKey;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.overflow.OverflowLog;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A session: a mounted tree, a host under it, and input routed the way a
/// window routes it.
///
/// What is asserted is the part a picture cannot show. That a click by id
/// reaches the node and a click on a covered node is refused rather than
/// quietly delivered to the cover; that what a widget floats over the window
/// through its host is drawn, takes the pointer, and leaves again by its own
/// handle; that the host's timers fire on the session's clock at the time
/// they were due; and that the overrun check answers for the whole tree
/// whatever the process-wide log has already said.
///
/// Read more:
/// [Driving input](https://goldberry.dev/docs/guide/testing.html#driving-input).
@DisplayName("a session")
class SessionTest {

    private static final String SHEET = """
            #root { width: 200px; height: 120px; flex-direction: column; }
            #a { width: 100px; height: 40px; }
            #b { width: 100px; height: 40px; }
            #veil { background: #ff0000; }
            """;

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A node that is a button to a reader, takes the keyboard, and writes
    /// down everything it is told — and keeps it, so the root under it hears
    /// nothing that landed on a child.
    private record Pad(@Nullable String id, List<Widget> children, List<String> log)
            implements Widget.Leaf, Styled, Paints, Handles, Semantics {

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                log.add("click:" + id);
                event.consume();
            }
        }

        @Override
        public void onKey(KeyEvent event) {
            // Tab is left to the router, which moves focus with it.
            if (event.kind() == KeyEvent.Kind.PRESSED && event.key() != Key.TAB) {
                log.add("key:" + id + ":" + event.key());
                event.consume();
            }
        }

        @Override
        public void onText(TextEvent event) {
            log.add("text:" + id + ":" + event.text());
            event.consume();
        }

        @Override
        public boolean wantsTextInput() {
            return id != null && !id.equals("root");
        }

        @Override
        public boolean isFocusable() {
            return id != null && !id.equals("root");
        }

        @Override
        public Role role() {
            return Role.BUTTON;
        }

        @Override
        public @Nullable String accessibleName() {
            return id;
        }
    }

    private Pad pad(String id, Widget... children) {
        return new Pad(id, List.of(children), log);
    }

    /// `#a` above `#b`, in a 200 by 120 window.
    private Session open() {
        return Offscreen.of(200, 120).stylesheets(sheet(SHEET)).session(pad("root", pad("a"), pad("b")));
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        @Test
        @DisplayName("clicks the node with an id at the centre of where it was drawn")
        void clickById() {
            try (var session = open()) {
                session.click("b");
                assertEquals(List.of("click:b"), log);
            }
        }

        @Test
        @DisplayName("and clicks whatever is at a point, given one")
        void clickAtAPoint() {
            try (var session = open()) {
                session.click(10, 10);
                assertEquals(List.of("click:a"), log);
            }
        }

        @Test
        @DisplayName("says which of three things went wrong when there is nothing to click")
        void nothingToClick() {
            try (var session = open()) {
                var missing = assertThrows(NoSuchElementException.class, () -> session.click("nope"));
                assertTrue(missing.getMessage().contains("nothing with id `nope`"), missing.getMessage());
            }
        }

        @Test
        @DisplayName("moves the pointer, and the router says what it is over")
        void hover() {
            try (var session = open()) {
                session.hover("b");
                assertEquals("b", session.hovered().orElseThrow().id());
            }
        }
    }

    @Nested
    @DisplayName("the host")
    class TheHost {

        @Test
        @DisplayName("is what the tree was built with")
        void theTreeHasAHost() {
            var seen = new AtomicBoolean();
            record Asks(AtomicBoolean seen) implements Widget.Stateful {

                @Override
                public State<?> createState() {
                    return new State<Asks>() {

                        @Override
                        public Widget build(BuildContext context) {
                            widget().seen().set(context.host().isPresent());
                            return new Pad("root", List.of(), new ArrayList<>());
                        }
                    };
                }
            }

            try (var session = Offscreen.of(200, 120).stylesheets(sheet(SHEET)).session(new Asks(seen))) {
                assertTrue(seen.get(), "a widget finds a host, which an Offscreen.render does not give it");
                assertEquals(
                        Duration.ZERO.toMillis(), (long) session.host().clock().nowMillis());
            }
        }

        @Test
        @DisplayName("draws what is floated over the window, on top of the content")
        void anOverlayIsDrawn() {
            try (var session = open()) {
                session.host().fill(pad("veil"));
                var picture = session.frame();
                assertEquals(RED, picture.argb(50, 20), "the veil covers #a");
                assertEquals(1, session.overlays().size());
            }
        }

        @Test
        @DisplayName("and a click on a node under it is refused, naming what would take it")
        void aCoveredNodeIsNotClicked() {
            try (var session = open()) {
                session.host().fill(pad("veil"));
                var covered = assertThrows(IllegalStateException.class, () -> session.click("a"));
                assertTrue(covered.getMessage().contains("#veil"), covered.getMessage());
                assertEquals(List.of(), log, "nothing was pressed");
            }
        }

        @Test
        @DisplayName("and the overlay's own handle takes it away again")
        void theHandleRemovesIt() {
            try (var session = open()) {
                var veil = session.host().fill(pad("veil"));
                assertTrue(veil.isAttached(), "attached, so remove() has something to do");
                veil.remove();
                session.click("a");
                assertEquals(List.of("click:a"), log);
                assertEquals(List.of(), session.overlays());
            }
        }

        @Test
        @DisplayName("has no popup windows and no window, which is what the dummy driver says")
        void noPopupsAndNoWindow() {
            try (var session = open()) {
                assertTrue(session.host()
                        .popup(pad("menu"), new LogicalPoint(0, 0), new LogicalSize(10, 10))
                        .isEmpty());
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> session.host().window());
            }
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("sends a key and text to what has focus")
        void keyAndText() {
            try (var session = open()) {
                assertTrue(session.focus("a"));
                session.key(Key.ENTER);
                session.type("hello");
                assertEquals(List.of("key:a:ENTER", "text:a:hello"), log);
            }
        }

        @Test
        @DisplayName("moves focus on Tab")
        void tab() {
            try (var session = open()) {
                session.focus("a");
                session.key(Key.TAB);
                assertEquals("b", session.focused().orElseThrow().id());
            }
        }

        @Test
        @DisplayName("fires an accelerator bound through the host, written as a menu prints it")
        void accelerator() {
            try (var session = open()) {
                session.host().shortcut("Ctrl+S", () -> log.add("saved"));
                session.key("Ctrl+S");
                assertEquals(List.of("saved"), log);
            }
        }

        @Test
        @DisplayName("keeps no modifier tap, since the gesture is a window's, and still refuses a null")
        void modifierTap() {
            try (var session = open()) {
                var owner = new Object();
                session.host().modifierTap(ModifierKey.CONTROL, () -> log.add("tapped"), owner);
                session.key("Ctrl+S");
                session.host().removeModifierTap(ModifierKey.CONTROL, owner);

                assertEquals(List.of(), log);
                assertThrows(
                        NullPointerException.class,
                        () -> session.host().modifierTap(ModifierKey.CONTROL, () -> {}, null));
                assertThrows(NullPointerException.class, () -> session.host().removeModifierTap(null, owner));
            }
        }
    }

    @Nested
    @DisplayName("the clock")
    class TheClock {

        @Test
        @DisplayName("does not move on input, and a timer waits for it")
        void timersWaitForTheClock() {
            try (var session = open()) {
                session.host().after(Duration.ofMillis(100), () -> log.add("fired"));
                session.click("a");
                session.advance(Duration.ofMillis(50));
                assertEquals(List.of("click:a"), log);
                session.advance(Duration.ofMillis(50));
                assertEquals(List.of("click:a", "fired"), log);
                assertEquals(100, session.nowMillis());
            }
        }

        @Test
        @DisplayName("stops at each timer on the way, rather than firing them all at the end")
        void eachAtItsTime() {
            try (var session = open()) {
                var host = session.host();
                host.after(Duration.ofMillis(30), () -> {
                    log.add("first@" + (long) host.clock().nowMillis());
                    host.after(
                            Duration.ofMillis(30),
                            () -> log.add("second@" + (long) host.clock().nowMillis()));
                });
                session.advance(Duration.ofMillis(100));
                assertEquals(List.of("first@30", "second@60"), log);
            }
        }

        @Test
        @DisplayName("and a timer with no delay fires on the next frame, as a window's does")
        void zeroDelay() {
            try (var session = open()) {
                session.host().after(Duration.ZERO, () -> log.add("next turn"));
                assertEquals(List.of(), log, "later, not now");
                session.frame();
                assertEquals(List.of("next turn"), log);
            }
        }

        @Test
        @DisplayName("refuses to run backwards")
        void forwards() {
            try (var session = open()) {
                assertThrows(IllegalArgumentException.class, () -> session.advance(Duration.ofMillis(-1)));
            }
        }
    }

    @Nested
    @DisplayName("the queries")
    class Queries {

        @Test
        @DisplayName("find a node by id, by role and name, and by where it was drawn")
        void finding() {
            try (var session = open()) {
                assertEquals("b", session.byId("b").orElseThrow().id());
                assertEquals("b", session.byRole(Role.BUTTON, "b").orElseThrow().id());
                assertEquals(3, session.byRole(Role.BUTTON).size());
                assertEquals("a", session.elementAt(10, 10).orElseThrow().id());
                assertFalse(session.regions().isEmpty());
            }
        }

        @Test
        @DisplayName("answer overruns for the whole tree, whatever the log already said")
        void overruns() {
            var tooNarrow = """
                    #root { width: 200px; height: 120px; }
                    #a { width: 100px; height: 40px; flex-direction: row; }
                    #b { width: 160px; height: 20px; flex-shrink: 0; }
                    """;
            for (var round = 0; round < 2; round++) {
                // The second round is the point: the log has said this shape
                // already and will not say it again, and the session still does.
                try (var session =
                        Offscreen.of(200, 120).stylesheets(sheet(tooNarrow)).session(pad("root", pad("a", pad("b"))))) {
                    assertEquals(
                            1, session.overruns().size(), session.overruns().toString());
                    assertEquals(60, session.overruns().getFirst().overrunX());
                }
            }
            assertNotEquals(List.of(), OverflowLog.reported(), "and the log heard it, once");
        }
    }

    @Nested
    @DisplayName("the lifetime")
    class Lifetime {

        @Test
        @DisplayName("ends at close, which may be called twice, and refuses input after it")
        void closes() {
            var session = open();
            session.close();
            session.close();
            assertThrows(IllegalStateException.class, () -> session.click("a"));
            assertThrows(IllegalStateException.class, session::frame);
        }
    }
}
