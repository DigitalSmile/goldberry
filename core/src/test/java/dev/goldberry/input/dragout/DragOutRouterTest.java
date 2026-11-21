package dev.goldberry.input.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.drop.Drop;
import dev.goldberry.input.drop.DropTarget;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
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

/// The router's half of a drag out: a draggable with an offer is handed over
/// at the threshold instead of becoming an in-app drag, and the platform's
/// reports drive its targets afterwards.
///
/// Driven through an offscreen [Session], whose router is a window's; the
/// window's own half (starting the platform's drag, the outcome, the swallowed
/// drop) is `DragOutWindowTest`'s.
@DisplayName("a drag out, as the router sees it")
class DragOutRouterTest {

    /// `#a` is the source, `#b` takes anything, `#c` takes nothing.
    private static final String SHEET = """
            #root { width: 400px; height: 120px; flex-direction: row; gap: 20px; padding: 10px; }
            tile { width: 60px; height: 40px; }
            #b { padding: 5px; }
            """;

    private final List<String> log = new ArrayList<>();

    private final List<Drop> drops = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private record Tile(String name, List<Widget> kids, Attributes attributes, List<String> log)
            implements Widget.Leaf, Styled, Paints, Handles, Attributed<Tile> {

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Tile withAttributes(Attributes value) {
            return new Tile(name, kids, value, log);
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
                case MOVED -> log.add("moved:" + name);
                case RELEASED -> log.add("released:" + name);
                case CLICKED -> log.add("click:" + name);
                default -> {}
            }
        }

        @Override
        public boolean isFocusable() {
            return true;
        }
    }

    private Tile tile(String name) {
        return new Tile(name, List.of(), Attributes.NONE.id(name), log);
    }

    private DropTarget taking(String name, boolean accepts) {
        return DropTarget.of(_ -> accepts, drop -> {
                    log.add("drop:" + name + ":" + drop.payload());
                    drops.add(drop);
                })
                .onLeave(() -> log.add("leave:" + name));
    }

    private Widget board(boolean offers) {
        var source = tile("a").draggable("card-a");
        if (offers) {
            source = source.dragOut(() -> DragOffer.text("card a"));
        }
        return new Tile(
                "root",
                List.of(
                        source,
                        tile("b").dropTarget(taking("b", true)),
                        tile("c").dropTarget(taking("c", false))),
                Attributes.NONE.id("root"),
                // A list of its own: everything bubbles to the root, and what the
                // tests read is what the tiles themselves heard.
                new ArrayList<>());
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

    /// Presses on `#a` and moves past the threshold, leaving the button down.
    private static LogicalPoint lift(Session session) {
        var from = centre(rect(session, "a"));
        var router = session.router();
        router.pointerMoved(from.x(), from.y());
        router.pointerPressed(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
        session.frame();
        router.pointerMoved(from.x() + PointerRouter.DRAG_THRESHOLD * 2, from.y());
        session.frame();
        return from;
    }

    @Test
    @DisplayName("a draggable with an offer is handed over at the threshold, not dragged in-app")
    void handsOver() {
        try (var session = open(board(true))) {
            var from = lift(session);
            var router = session.router();

            assertTrue(router.isDraggingOut());
            assertTrue(router.dragging().isEmpty(), "no in-app drag, so no ghost");
            var handOff = router.takeDragOut().orElseThrow();
            assertSame(byId(session, "a"), handOff.source());
            assertEquals("card-a", handOff.payload());
            assertEquals(rect(session, "a"), handOff.from());
            assertEquals(from.x() - handOff.from().left(), handOff.grab().x(), 0.01);
            assertEquals(
                    from.x() + PointerRouter.DRAG_THRESHOLD * 2,
                    handOff.pointer().x(),
                    0.01);
            assertEquals("card a", handOff.out().offer().get().text().get());
            assertTrue(router.takeDragOut().isEmpty(), "taken once");

            log.clear();
            var over = centre(rect(session, "b"));
            router.pointerMoved(over.x(), over.y());
            assertEquals(List.of(), log, "the source hears no moves while the platform has the drag");
            assertFalse(
                    byId(session, "b").hasState(PseudoClass.DRAG_OVER), "nor do targets, from the window's pointer");
        }
    }

    @Test
    @DisplayName("a draggable without one is an in-app drag as before")
    void noOffer() {
        try (var session = open(board(false))) {
            lift(session);

            assertFalse(session.router().isDraggingOut());
            assertTrue(session.router().takeDragOut().isEmpty());
            assertTrue(session.router().dragging().isPresent());
        }
    }

    @Test
    @DisplayName("refused, it is an in-app drag from where the pointer is, and lands as one")
    void refused() {
        try (var session = open(board(true))) {
            lift(session);
            var router = session.router();
            router.dragOutRefused(router.takeDragOut().orElseThrow());

            assertFalse(router.isDraggingOut());
            var dragging = router.dragging().orElseThrow();
            assertFalse(dragging.platform(), "the toolkit carries it, ghost and all");

            var at = centre(rect(session, "b"));
            router.pointerMoved(at.x(), at.y());
            router.pointerReleased(at.x(), at.y(), PointerEvent.Button.PRIMARY, 1);
            assertEquals(1, drops.size(), log::toString);
            assertEquals("card-a", drops.getFirst().payload());
            assertFalse(log.contains("click:a"), log::toString);
        }
    }

    @Test
    @DisplayName("the platform's reports light a target, and its drop lands the payload there")
    void platformDropLands() {
        try (var session = open(board(true))) {
            lift(session);
            var router = session.router();
            var handOff = router.takeDragOut().orElseThrow();
            var b = rect(session, "b");
            var at = centre(b);

            router.platformDragOver(
                    handOff.source(), handOff.payload(), handOff.from(), handOff.grab(), at.x(), at.y());
            assertTrue(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
            var dragging = router.dragging().orElseThrow();
            assertTrue(dragging.platform(), "the platform draws the picture");
            assertSame(byId(session, "b"), dragging.target());

            assertTrue(router.platformDragDropped(at.x(), at.y()));
            assertEquals(1, drops.size());
            assertEquals("card-a", drops.getFirst().payload());
            assertEquals(at.x() - b.left() - 5, drops.getFirst().at().x(), 0.01, "in b's content box");
            assertFalse(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
            assertTrue(router.dragging().isEmpty());
        }
    }

    @Test
    @DisplayName("a platform drop over a target that refuses lands nowhere")
    void platformDropRefused() {
        try (var session = open(board(true))) {
            lift(session);
            var router = session.router();
            var handOff = router.takeDragOut().orElseThrow();
            var at = centre(rect(session, "c"));

            router.platformDragOver(
                    handOff.source(), handOff.payload(), handOff.from(), handOff.grab(), at.x(), at.y());
            assertFalse(router.platformDragDropped(at.x(), at.y()));
            assertEquals(List.of(), drops);
        }
    }

    @Test
    @DisplayName("leaving runs the target's onLeave and takes :drag-over off")
    void platformLeaves() {
        try (var session = open(board(true))) {
            lift(session);
            var router = session.router();
            var handOff = router.takeDragOut().orElseThrow();
            var at = centre(rect(session, "b"));

            router.platformDragOver(
                    handOff.source(), handOff.payload(), handOff.from(), handOff.grab(), at.x(), at.y());
            router.platformDragLeft();

            assertTrue(log.contains("leave:b"), log::toString);
            assertFalse(byId(session, "b").hasState(PseudoClass.DRAG_OVER));
            assertTrue(router.dragging().isEmpty());
            assertFalse(router.platformDragDropped(at.x(), at.y()), "nothing left to drop");
        }
    }

    @Test
    @DisplayName("finished while the button is down, the press is over and its late release is heard by nobody")
    void finishedBeforeTheRelease() {
        try (var session = open(board(true))) {
            var from = lift(session);
            var router = session.router();
            router.takeDragOut().orElseThrow();
            router.dragOutFinished();

            assertFalse(router.isDraggingOut());
            assertFalse(byId(session, "a").hasState(PseudoClass.ACTIVE), "no longer pressed");
            log.clear();
            router.pointerReleased(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
            assertEquals(List.of(), log, "the late release is swallowed");

            // The next gesture is an ordinary one.
            var c = centre(rect(session, "c"));
            router.pointerMoved(c.x(), c.y());
            router.pointerPressed(c.x(), c.y(), PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(c.x(), c.y(), PointerEvent.Button.PRIMARY, 1);
            assertEquals(List.of("moved:c", "released:c", "click:c"), log);
        }
    }

    @Test
    @DisplayName("finished after the release, the release was no click and nothing is swallowed later")
    void finishedAfterTheRelease() {
        try (var session = open(board(true))) {
            var from = lift(session);
            var router = session.router();
            router.takeDragOut().orElseThrow();
            router.pointerReleased(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
            assertTrue(log.contains("released:a"), log::toString);
            assertFalse(log.contains("click:a"), "a press that became a drag is no click");
            router.dragOutFinished();

            log.clear();
            router.pointerPressed(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(from.x(), from.y(), PointerEvent.Button.PRIMARY, 1);
            assertEquals(List.of("released:a", "click:a"), log);
        }
    }

    @Test
    @DisplayName("Escape while the platform has the drag is taken and does nothing of its own")
    void escapeIsThePlatforms() {
        try (var session = open(board(true))) {
            lift(session);
            var router = session.router();

            assertTrue(router.keyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertTrue(router.isDraggingOut(), "the platform reports the cancel");
        }
    }

    @Test
    @DisplayName("a keyboard drag of a draggable with an offer stays in the window")
    void keyboardStaysIn() {
        try (var session = open(board(true))) {
            var router = session.router();
            router.focusById("a", true);
            session.frame();

            assertTrue(router.keyPressed(Key.SPACE, Modifiers.NONE, false));
            assertTrue(router.dragging().orElseThrow().fromKeyboard());
            assertTrue(router.takeDragOut().isEmpty());
            assertFalse(router.isDraggingOut());
        }
    }
}
