package dev.goldberry.offscreen;

import static dev.goldberry.offscreen.Scene.GREEN;
import static dev.goldberry.offscreen.Scene.RED;
import static dev.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Styled;

/// `view-transition-name` from a stylesheet to the screen, through the frame
/// sequence a window runs: the move is timed by the box's own `transition`,
/// keeps the frame loop awake until it lands, and takes the pointer with it.
///
/// A 200 × 200 board whose green gap, 100 high, opens above a red tile and
/// pushes it from the top to `y = 100` over 100 ms, linearly.
@DisplayName("a view transition in a session")
class ViewTransitionSessionTest {

    private static final String SHEET = """
            #board { flex-direction: column; width: 200px; height: 200px; background-color: #000000 }
            #gap { width: 50px; height: 100px; background-color: #00ff00 }
            #tile {
              width: 50px; height: 50px; background-color: #ff0000;
              view-transition-name: tile; transition: transform 100ms linear
            }
            """;

    /// The board, whose gap opens when `open` is run.
    private record Board(AtomicReference<Runnable> open) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new State<Board>() {

                private boolean gap;

                @Override
                protected void initState() {
                    widget().open().set(() -> setState(() -> gap = true));
                }

                @Override
                public Widget build(BuildContext context) {
                    var children = new ArrayList<Widget>();
                    if (gap) {
                        children.add(new Scene.Panel(List.of(), id("gap")));
                    }
                    children.add(new Scene.Panel(List.of(), id("tile")));
                    return new Scene.Panel(children, id("board"));
                }
            };
        }
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("draws the tile where it was, carries it to where layout put it, and lets the loop rest")
    void moves() {
        var open = new AtomicReference<Runnable>();
        try (var session = Offscreen.of(200, 200).stylesheets(sheet(SHEET)).session(new Board(open))) {
            assertEquals(RED, session.frame().argb(25, 25));

            open.get().run();
            var start = session.frame();
            assertEquals(RED, start.argb(25, 25), "drawn where it was, over the gap");
            assertTrue(session.isAnimating(), "a frame loop keeps asking while it moves");
            assertEquals("tile", idAt(session, 25, 25), "and the pointer finds it where it is drawn");

            session.advance(Duration.ofMillis(50));
            var halfway = session.frame();
            assertEquals(GREEN, halfway.argb(25, 25));
            assertEquals(RED, halfway.argb(25, 75), "halfway, by the box's own transition");
            assertEquals("tile", idAt(session, 25, 75));

            session.advance(Duration.ofMillis(50));
            var arrived = session.frame();
            assertEquals(RED, arrived.argb(25, 125));
            assertEquals(GREEN, arrived.argb(25, 75));
            assertFalse(session.isAnimating(), "landed, so the loop may rest");
        }
    }

    private static String idAt(Session session, float x, float y) {
        return session.elementAt(x, y)
                .map(Element::widget)
                .filter(Styled.class::isInstance)
                .map(widget -> ((Styled) widget).id())
                .orElse("nothing");
    }
}
