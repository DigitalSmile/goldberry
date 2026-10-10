package dev.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.input.drop.Drop;
import dev.goldberry.input.drop.DropTarget;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Row;

/// A `canvas` as a drop target, and a widget positioned on one as a drag
/// source: the two halves of moving an event to another day on a painted week.
///
/// The point a canvas is handed is the painter's own, measured from the corner
/// of its content box, so the arithmetic that drew the grid reads the drop.
@DisplayName("a canvas and a drag")
class CanvasDropTest {

    private static final String SHEET = """
            row { gap: 20px; padding: 10px; }
            pressable { width: 60px; height: 40px; }
            canvas { width: 300px; height: 200px; padding: 10px; }
            """;

    private final List<Drop> drops = new ArrayList<>();

    private final List<LogicalPoint> overs = new ArrayList<>();

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private DropTarget week() {
        return DropTarget.of(payload -> payload instanceof String, drops::add)
                .whileOver(drop -> overs.add(drop.at()))
                .onLeave(() -> log.add("leave"));
    }

    private static Session open(Widget root) {
        return Offscreen.of(500, 240)
                .stylesheets(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET)))
                .session(root);
    }

    private static Element byId(Session session, String id) {
        return session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
    }

    private static LogicalRect content(Session session, String id) {
        var element = byId(session, id);
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow()
                .content();
    }

    @Test
    @DisplayName("a canvas that is a target hears where the drag is, and the drop, in the painter's coordinates")
    void canvasTarget() {
        var root = new Row(
                new Pressable("Standup", () -> log.add("opened")).id("card").draggable("standup"),
                new Canvas((frame, size) -> {}).id("week").dropTarget(week()));
        try (var session = open(root)) {
            var grid = content(session, "week");
            var card = content(session, "card");
            var router = session.router();
            var fromX = card.left() + 10;
            var fromY = card.top() + 10;
            router.pointerMoved(fromX, fromY);
            router.pointerPressed(fromX, fromY, PointerEvent.Button.PRIMARY, 1);
            session.frame();
            router.pointerMoved(grid.left() + 40, grid.top() + 70);
            session.frame();

            assertTrue(byId(session, "week").hasState(PseudoClass.DRAG_OVER));
            assertEquals(new LogicalPoint(40, 70), overs.getLast());

            router.pointerMoved(grid.left() + 120, grid.top() + 90);
            router.pointerReleased(grid.left() + 120, grid.top() + 90, PointerEvent.Button.PRIMARY, 1);
            session.frame();

            assertEquals(List.of(new Drop("standup", new LogicalPoint(120, 90))), drops);
            assertEquals(List.of("leave"), log, "left once, as it was dropped, and never opened");
            assertFalse(byId(session, "week").hasState(PseudoClass.DRAG_OVER));
        }
    }

    @Test
    @DisplayName("a widget positioned on the canvas is carried across it and dropped on it")
    void positionedSource() {
        var week = new Canvas((frame, size) -> {})
                .id("week")
                .dropTarget(week())
                .overlay(size -> List.of(new Positioned(
                        new Pressable("Standup", () -> log.add("opened"))
                                .id("event")
                                .draggable("standup"),
                        LogicalRect.of(20, 20, 60, 30))));
        try (var session = open(new Row(week))) {
            var grid = content(session, "week");
            session.drag(grid.left() + 30, grid.top() + 30, grid.left() + 200, grid.top() + 150);

            assertEquals(List.of(new Drop("standup", new LogicalPoint(200, 150))), drops);
            assertEquals(List.of("leave"), log);

            session.click("event");
            assertEquals(List.of("leave", "opened"), log, "and a press that does not move still opens it");
        }
    }
}
