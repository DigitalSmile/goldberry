package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.event.SdlEventType;

/// The bookkeeping around a Windows drag, with the drag played by the test:
/// how long it takes SDL's drop events, and how it ends.
@DisplayName("a drag out on Windows, around DoDragDrop")
class WindowsDragOutTest {

    /// A drag that ends at once, or never begins.
    private static final class Played implements WindowsDragOut.HeldDrag {

        final boolean begins;
        boolean cancelled;

        Played(boolean begins) {
            this.begins = begins;
        }

        @Override
        public boolean run() {
            return begins;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static boolean takes(WindowsDragOut drag) {
        return drag.takesDrop(SdlEventType.DROP_FILE.value(), null, 0, 0);
    }

    @Test
    @DisplayName("takes SDL's drop events while it runs and through the pump after its end, then closes")
    void echoes() {
        var drag = new WindowsDragOut(new Played(true));
        assertTrue(takes(drag), "while it runs, nothing SDL's target hears is a drop from outside");

        assertTrue(drag.run());
        drag.tick();
        assertFalse(drag.isClosed(), "the first pump after reads what SDL's target queued");
        assertTrue(takes(drag));

        drag.tick();
        assertTrue(drag.isClosed());
        assertFalse(takes(drag));
    }

    @Test
    @DisplayName("closes at once when OLE refused it")
    void refused() {
        var drag = new WindowsDragOut(new Played(false));

        assertFalse(drag.run());
        assertTrue(drag.isClosed());
    }

    @Test
    @DisplayName("cancels the drag when closed while it runs, and not after")
    void close() {
        var running = new Played(true);
        var drag = new WindowsDragOut(running);
        drag.close();
        assertTrue(running.cancelled);

        var ended = new Played(true);
        var after = new WindowsDragOut(ended);
        after.run();
        after.close();
        assertFalse(ended.cancelled);
        assertTrue(after.isClosed());
    }
}
