package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// Where a Wayland drag ended, judged from SDL's drop events and the source's
/// own, in the orders mutter and weston send them.
@DisplayName("a Wayland drag, judged from SDL's drops and the source's events")
class WaylandDropTrackerTest {

    private final BackendWindow board = window("board");
    private final BackendWindow chat = window("chat");
    private final Recorder session = new Recorder();
    private final WaylandDropTracker tracker = new WaylandDropTracker(session);

    @Nested
    @DisplayName("over the application's own windows")
    class Own {

        @Test
        @DisplayName("reports each position SDL hears to the session")
        void positions() {
            tracker.over(board, new LogicalPoint(10, 20));
            tracker.over(board, new LogicalPoint(12, 22));

            assertEquals(List.of("over board 10,20", "over board 12,22"), session.heard);
        }

        @Test
        @DisplayName("lands where it was let go when the drop finishes with no target heard after")
        void landed() {
            tracker.over(board, new LogicalPoint(10, 20));
            // SDL's drop handler: the complete, then the source hears the drop
            // and, after SDL's finish, the end.
            tracker.left(board);
            tracker.dropPerformed();
            tracker.finished(DragAction.COPY);

            assertEquals(List.of("over board 10,20", "outside", "released board 10,20"), session.heard);
        }

        @Test
        @DisplayName("still lands when mutter says null after the drop, as it ends its grab")
        void nullAfterDrop() {
            tracker.over(board, new LogicalPoint(10, 20));
            tracker.left(board);
            tracker.dropPerformed();
            tracker.target(null);
            tracker.finished(DragAction.COPY);

            assertEquals("released board 10,20", session.heard.getLast());
        }

        @Test
        @DisplayName("lands on the second window after moving across from the first")
        void across() {
            tracker.over(board, new LogicalPoint(10, 20));
            tracker.left(board);
            tracker.target(null);
            tracker.over(chat, new LogicalPoint(3, 4));
            tracker.left(chat);
            tracker.dropPerformed();
            tracker.finished(null);

            assertEquals("released chat 3,4", session.heard.getLast());
        }
    }

    @Nested
    @DisplayName("out to another application")
    class Elsewhere {

        @Test
        @DisplayName("is delivered when mutter says null as the pointer leaves")
        void mutter() {
            tracker.over(board, new LogicalPoint(10, 20));
            tracker.left(board);
            tracker.target(null);
            tracker.target("text/uri-list");
            tracker.dropPerformed();
            tracker.finished(DragAction.MOVE);

            assertEquals("delivered MOVE", session.heard.getLast());
        }

        @Test
        @DisplayName("is delivered when weston says only what the other target took")
        void weston() {
            tracker.over(board, new LogicalPoint(10, 20));
            tracker.left(board);
            tracker.target("image/png");
            tracker.dropPerformed();
            tracker.finished(null);

            assertEquals("delivered null", session.heard.getLast());
        }

        @Test
        @DisplayName("is delivered when it never came over a window of the application's")
        void never() {
            tracker.target("text/plain");
            tracker.dropPerformed();
            tracker.finished(DragAction.COPY);

            assertEquals(List.of("delivered COPY"), session.heard);
        }
    }

    @Test
    @DisplayName("is cancelled when the compositor cancels, wherever it was")
    void cancelled() {
        tracker.over(board, new LogicalPoint(10, 20));
        tracker.left(board);
        tracker.cancelled();

        assertEquals("cancelled", session.heard.getLast());
    }

    @Test
    @DisplayName("says nothing after its ending, and ends once")
    void endsOnce() {
        tracker.cancelled();
        tracker.over(board, new LogicalPoint(1, 1));
        tracker.left(board);
        tracker.finished(DragAction.COPY);
        tracker.cancelled();

        assertEquals(List.of("cancelled"), session.heard);
    }

    @Test
    @DisplayName("does not take a complete over one window as a release over another")
    void otherWindowsComplete() {
        tracker.over(board, new LogicalPoint(10, 20));
        tracker.left(chat);
        tracker.dropPerformed();
        tracker.finished(DragAction.COPY);

        assertEquals("delivered COPY", session.heard.getLast());
    }

    private static BackendWindow window(String name) {
        return (BackendWindow) Proxy.newProxyInstance(
                BackendWindow.class.getClassLoader(),
                new Class<?>[] {BackendWindow.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /// The session's side, as lines.
    private static final class Recorder implements DragSession {

        final List<String> heard = new ArrayList<>();

        @Override
        public void over(BackendWindow window, LogicalPoint at) {
            heard.add("over " + window + " " + (int) at.x() + "," + (int) at.y());
        }

        @Override
        public void outside() {
            heard.add("outside");
        }

        @Override
        public void ended(DragEnd end) {
            heard.add(
                    switch (end) {
                        case DragEnd.Delivered(var operation) -> "delivered " + operation;
                        case DragEnd.ReleasedOver(var window, var at, var _) ->
                            "released " + window + " " + (int) at.x() + "," + (int) at.y();
                        case DragEnd.Cancelled _ -> "cancelled";
                    });
        }
    }
}
