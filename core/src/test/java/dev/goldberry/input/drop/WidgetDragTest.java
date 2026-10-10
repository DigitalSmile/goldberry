package dev.goldberry.input.drop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A drag from one widget onto another: `Attributes.draggable` on the source,
/// `Attributes.dropTarget` on the target, and the pointer router between them.
///
/// Driven through a [Session], so the press, the moves and the release go
/// through the same router and the same frames a window's do, and the ghost is
/// laid out with the rest of the tree.
@DisplayName("a drag between widgets")
class WidgetDragTest {

    /// Four tiles in a row, 60 by 40, 20 apart; `#d` sits inside `#shelf`.
    private static final String SHEET = """
            #root { width: 400px; height: 120px; flex-direction: row; gap: 20px; padding: 10px; }
            tile { width: 60px; height: 40px; }
            #b { padding: 5px; }
            #shelf { width: 100px; height: 80px; padding: 10px; }
            """;

    private final List<String> log = new ArrayList<>();

    private final List<Drop> drops = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A box that hears clicks, takes the keyboard when asked to, and consumes
    /// the press when told to.
    private record Tile(
            String name, List<Widget> kids, Attributes attributes, boolean focusable, boolean grabs, List<String> log)
            implements Widget.Leaf, Styled, Paints, Handles, Attributed<Tile> {

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Tile withAttributes(Attributes value) {
            return new Tile(name, kids, value, focusable, grabs, log);
        }

        @Override
        public String cssType() {
            return "tile";
        }

        @Override
        public @Nullable String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public @Nullable Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }

        @Override
        public void onPointer(PointerEvent event) {
            switch (event.kind()) {
                case PRESSED -> {
                    if (grabs) {
                        log.add("grabbed:" + name);
                        event.consume();
                    }
                }
                case RELEASED -> log.add("released:" + name);
                case CLICKED -> {
                    log.add("click:" + name);
                    event.consume();
                }
                default -> {}
            }
        }

        @Override
        public boolean isFocusable() {
            return focusable;
        }
    }

    private Tile tile(String name, Widget... kids) {
        return new Tile(name, List.of(kids), Attributes.NONE.id(name), false, false, log);
    }

    private DropTarget taking(String name, Function<Object, Boolean> accepts) {
        return DropTarget.of(accepts::apply, drop -> {
                    log.add("drop:" + name + ":" + drop.payload());
                    drops.add(drop);
                })
                .onLeave(() -> log.add("leave:" + name));
    }

    private Widget board() {
        return new Tile(
                "root",
                List.of(
                        tile("a").draggable("card-a"),
                        tile("b").dropTarget(taking("b", payload -> true)),
                        tile("c").dropTarget(taking("c", payload -> false)),
                        tile("shelf", tile("d").dropTarget(taking("d", payload -> false)))
                                .dropTarget(taking("shelf", payload -> payload.equals("card-a")))),
                Attributes.NONE.id("root"),
                false,
                false,
                log);
    }

    private static Session open(Widget root) {
        return Offscreen.of(400, 120)
                .stylesheets(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)))
                .session(root);
    }

    private static Element byId(Session session, String id) {
        return session.byId(id).orElseThrow();
    }

    private static LogicalRect rect(Session session, String id) {
        var element = byId(session, id);
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow()
                .painted();
    }

    private static LogicalPoint centre(LogicalRect rect) {
        return new LogicalPoint(rect.left() + rect.width() / 2, rect.top() + rect.height() / 2);
    }

    /// Presses at `from`, moves past the threshold and on to `to`, and leaves
    /// the button down.
    private static void lift(Session session, LogicalPoint from, LogicalPoint to) {
        var router = session.router();
        router.pointerMoved(from.x(), from.y());
        router.pointerPressed(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
        session.frame();
        router.pointerMoved(from.x() + PointerRouter.DRAG_THRESHOLD * 2, from.y());
        session.frame();
        router.pointerMoved(to.x(), to.y());
        session.frame();
    }

    @Nested
    @DisplayName("with the pointer")
    class Pointer {

        @Test
        @DisplayName("from A onto B delivers the payload, at the point in B's content box")
        void delivers() {
            try (var session = open(board())) {
                var b = rect(session, "b");
                var at = centre(b);
                session.drag("a", "b");

                assertEquals(1, drops.size(), log::toString);
                assertEquals("card-a", drops.getFirst().payload());
                // B has 5px of padding, so its content box starts 5px in.
                assertEquals(at.x() - b.left() - 5, drops.getFirst().at().x(), 0.01);
                assertEquals(at.y() - b.top() - 5, drops.getFirst().at().y(), 0.01);
                assertFalse(drops.getFirst().fromKeyboard());
                assertTrue(log.contains("released:a"), "the source hears its press end: " + log);
                assertFalse(log.contains("click:a"), "a press that became a drag is not a click: " + log);
                assertEquals(List.of("leave:b", "drop:b:card-a"), log.subList(log.size() - 2, log.size()));
                assertTrue(session.router().dragging().isEmpty());
                assertFalse(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
            }
        }

        @Test
        @DisplayName("over B, B matches :drag-over and the drag says it is the target")
        void dragOver() {
            try (var session = open(board())) {
                lift(session, centre(rect(session, "a")), centre(rect(session, "b")));

                var dragging = session.router().dragging().orElseThrow();
                assertSame(byId(session, "a"), dragging.source());
                assertSame(byId(session, "b"), dragging.target());
                assertTrue(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
                assertFalse(byId(session, "root").hasState(PseudoClass.DRAG_OVER), "the target alone");
            }
        }

        @Test
        @DisplayName("a target that refuses gets no :drag-over and no drop")
        void refused() {
            try (var session = open(board())) {
                lift(session, centre(rect(session, "a")), centre(rect(session, "c")));

                assertNull(session.router().dragging().orElseThrow().target());
                assertFalse(byId(session, "c").hasState(PseudoClass.DRAG_OVER));

                var at = centre(rect(session, "c"));
                session.router().pointerReleased(at.x(), at.y(), PointerEvent.Button.PRIMARY, 1);
                session.frame();
                assertEquals(List.of(), drops);
                assertFalse(log.contains("leave:c"), "a target that refused never heard the drag: " + log);
            }
        }

        @Test
        @DisplayName("a target that refuses passes the drag to an ancestor that accepts")
        void refusedGoesUp() {
            try (var session = open(board())) {
                session.drag("a", "d");

                assertEquals(
                        List.of("drop:shelf:card-a"),
                        log.stream().filter(line -> line.startsWith("drop:")).toList());
            }
        }

        @Test
        @DisplayName("Escape puts it back: no drop, no :drag-over, and the release is no click")
        void escapeCancels() {
            try (var session = open(board())) {
                lift(session, centre(rect(session, "a")), centre(rect(session, "b")));
                session.key(Key.ESCAPE);

                assertTrue(session.router().dragging().isEmpty());
                assertFalse(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
                assertTrue(log.contains("leave:b"), log::toString);

                var at = centre(rect(session, "b"));
                session.router().pointerReleased(at.x(), at.y(), PointerEvent.Button.PRIMARY, 1);
                session.frame();
                assertEquals(List.of(), drops);
                assertFalse(log.contains("click:a"), log::toString);
            }
        }

        @Test
        @DisplayName("a press and a release that do not move are a click")
        void stillAClick() {
            try (var session = open(board())) {
                session.click("a");
                assertEquals(
                        List.of("click:a"),
                        log.stream().filter(line -> line.startsWith("click:")).toList());
                assertTrue(session.router().dragging().isEmpty());

                // Under the threshold is still not a drag.
                var at = centre(rect(session, "a"));
                var router = session.router();
                router.pointerPressed(at.x(), at.y(), PointerEvent.Button.PRIMARY, 1);
                router.pointerMoved(at.x() + PointerRouter.DRAG_THRESHOLD / 2, at.y());
                assertTrue(router.dragging().isEmpty());
                router.pointerReleased(
                        at.x() + PointerRouter.DRAG_THRESHOLD / 2, at.y(), PointerEvent.Button.PRIMARY, 1);
                assertEquals(2, log.stream().filter("click:a"::equals).count(), log::toString);
                assertEquals(List.of(), drops);
            }
        }

        @Test
        @DisplayName("a control inside the draggable that takes the press keeps it")
        void innerControlKeepsThePress() {
            var knob = new Tile("knob", List.of(), Attributes.NONE.id("knob"), false, true, log);
            var root = new Tile(
                    "root",
                    List.of(tile("a", knob).draggable("card-a"), tile("b").dropTarget(taking("b", payload -> true))),
                    Attributes.NONE.id("root"),
                    false,
                    false,
                    log);
            try (var session = open(root)) {
                session.drag("knob", "b");

                assertTrue(log.contains("grabbed:knob"), log::toString);
                assertEquals(List.of(), drops);
            }
        }

        @Test
        @DisplayName("the ghost follows the pointer and the hit test sees through it")
        void ghost() {
            try (var session = open(board())) {
                var a = rect(session, "a");
                var from = centre(a);
                var to = centre(rect(session, "b"));
                lift(session, from, to);

                var ghost = session.regions().stream()
                        .filter(region -> region.owner() == null)
                        .filter(region -> region.width() == a.width() && region.height() == a.height())
                        .map(HitTest.Region::painted)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no ghost among " + session.regions()));
                assertEquals(to.x() - (from.x() - a.left()), ghost.left(), 0.01);
                assertEquals(to.y() - (from.y() - a.top()), ghost.top(), 0.01);
                assertSame(byId(session, "b"), session.elementAt(to.x(), to.y()).orElseThrow());
            }
        }
    }

    @Nested
    @DisplayName("with the keyboard")
    class Keyboard {

        private Widget board() {
            return new Tile(
                    "root",
                    List.of(
                            new Tile("a", List.of(), Attributes.NONE.id("a"), true, false, log).draggable("card-a"),
                            tile("b").dropTarget(taking("b", payload -> true)),
                            tile("c").dropTarget(taking("c", payload -> false)),
                            tile("shelf").dropTarget(taking("shelf", payload -> true))),
                    Attributes.NONE.id("root"),
                    false,
                    false,
                    log);
        }

        @Test
        @DisplayName("Space lifts the focused draggable, Tab steps to the next target, Enter drops at its centre")
        void liftStepDrop() {
            try (var session = open(board())) {
                assertTrue(session.focus("a"));
                session.key(Key.SPACE);

                var dragging = session.router().dragging().orElseThrow();
                assertTrue(dragging.fromKeyboard());
                assertSame(byId(session, "b"), dragging.target(), "the first target that accepts");
                assertTrue(byId(session, "b").hasState(PseudoClass.DRAG_OVER));

                session.key(Key.TAB);
                assertSame(
                        byId(session, "shelf"),
                        session.router().dragging().orElseThrow().target(),
                        "the refusing `c` is stepped over");
                assertSame(byId(session, "a"), session.focused().orElseThrow(), "the keyboard stays on the source");

                session.key(Key.ENTER);
                assertTrue(session.router().dragging().isEmpty());
                var shelf = rect(session, "shelf");
                assertEquals(1, drops.size(), log::toString);
                assertTrue(drops.getFirst().fromKeyboard());
                assertEquals(shelf.width() / 2 - 10, drops.getFirst().at().x(), 0.01);
                assertEquals(shelf.height() / 2 - 10, drops.getFirst().at().y(), 0.01);
            }
        }

        @Test
        @DisplayName("Escape puts a keyboard drag back")
        void escape() {
            try (var session = open(board())) {
                session.focus("a");
                session.key(Key.SPACE);
                session.key(Key.ESCAPE);

                assertTrue(session.router().dragging().isEmpty());
                assertFalse(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
                assertEquals(List.of(), drops);
            }
        }

        @Test
        @DisplayName("Space on a focused node that is not draggable is the node's")
        void notDraggable() {
            try (var session = open(board())) {
                session.router().keyPressed(Key.SPACE, Modifiers.NONE, false);
                assertTrue(session.router().dragging().isEmpty());
            }
        }
    }
}
