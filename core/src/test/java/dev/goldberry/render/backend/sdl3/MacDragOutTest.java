package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.event.SdlEventType;
import dev.goldberry.render.model.LogicalPoint;

/// The bookkeeping around a macOS drag, with AppKit's session played by the
/// test, and its points made the toolkit's.
@DisplayName("a drag out on macOS, around the session")
class MacDragOutTest {

    /// A session that ends when told to.
    private static final class Played implements MacDragOut.Session {

        boolean ended;
        boolean closed;

        @Override
        public boolean isEnded() {
            return ended;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static boolean takes(MacDragOut drag) {
        return drag.takesDrop(SdlEventType.DROP_TEXT.value(), null, 0, 0);
    }

    @Test
    @DisplayName("takes SDL's drop events while the session runs and through the pump after its end, then closes")
    void echoes() {
        var session = new Played();
        var drag = new MacDragOut(session);

        drag.tick();
        assertTrue(takes(drag), "while it runs, SDL's view hears the application's own offer");
        assertFalse(drag.isClosed());

        session.ended = true;
        drag.tick();
        assertFalse(drag.isClosed(), "the pump after the end still reads SDL's echo");
        assertTrue(takes(drag));

        drag.tick();
        assertTrue(drag.isClosed());
        assertFalse(takes(drag));
        assertFalse(session.closed, "the source object waits for the pasteboard, not for the backend");
    }

    @Test
    @DisplayName("lets the source object go when the backend closes it")
    void close() {
        var session = new Played();
        var drag = new MacDragOut(session);

        drag.close();

        assertTrue(session.closed);
        assertTrue(drag.isClosed());
    }

    @Test
    @DisplayName("turns AppKit's points from the bottom into logical pixels from the top")
    void points() {
        assertEquals(new LogicalPoint(40, 70), MacDragOut.Reports.fromTop(100, 40, 30));
    }
}
