package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.bind.Subscription;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.stats.FrameRecord;
import dev.goldberry.stats.FrameSample;

/// A window's frame listener: one call per presented frame, with a view that
/// is the same object every time and a copy that keeps.
@DisplayName("a window's frame listener")
class WindowFrameListenerTest {

    private static final int FRAMES = 4;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        GoldberryRuntime.install(new HeadlessBackend());
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("hears every presented frame in order, through one reused view")
    void everyFrame() {
        var window = Window.open(WindowSpec.of("frames", LogicalSize.of(80f, 60f)));
        var heard = new ArrayList<FrameRecord>();
        Set<FrameSample> views = new HashSet<>();
        window.onFrame(frame -> {
            views.add(frame);
            heard.add(frame.copy());
        });
        var painted = new int[1];
        window.onPaint(frame -> {
            if (++painted[0] >= FRAMES) {
                Goldberry.stop();
            } else {
                window.repaint();
            }
        });
        Goldberry.run();

        assertEquals(FRAMES, heard.size(), "one call a frame: " + heard);
        assertEquals(1, views.size(), "the same view every frame");
        assertEquals(
                List.of(1L, 2L, 3L, 4L), heard.stream().map(FrameRecord::number).toList());
        assertEquals(0, heard.getFirst().intervalNanos(), "the first frame has no interval");
        for (var i = 1; i < heard.size(); i++) {
            var frame = heard.get(i);
            assertEquals(
                    frame.finishedNanos() - heard.get(i - 1).finishedNanos(),
                    frame.intervalNanos(),
                    "an interval is the distance between two frames' ends");
            assertTrue(frame.paintNanos() >= 0);
            assertFalse(frame.composited(), "the headless backend presents on the CPU");
        }
        assertEquals(FRAMES, window.frames().count());
    }

    @Test
    @Timeout(10)
    @DisplayName("stops hearing once its subscription is closed, even from inside its own call")
    void unsubscribes() {
        var window = Window.open(WindowSpec.of("frames", LogicalSize.of(80f, 60f)));
        var once = new int[1];
        var always = new int[1];
        var subscription = new Subscription[1];
        subscription[0] = window.onFrame(frame -> {
            once[0]++;
            subscription[0].close();
        });
        window.onFrame(frame -> always[0]++);
        var painted = new int[1];
        window.onPaint(frame -> {
            if (++painted[0] >= FRAMES) {
                Goldberry.stop();
            } else {
                window.repaint();
            }
        });
        Goldberry.run();

        assertEquals(1, once[0], "closed in its first call");
        assertEquals(FRAMES, always[0], "the other listener is untouched");
        subscription[0].close();
        assertThrows(NullPointerException.class, () -> window.onFrame(null));
    }

    @Test
    @DisplayName("keeps a copy as it was")
    void copyKeeps() {
        var record = new FrameRecord(7, 100, 16, 3, 1, 1, 0, 1, 0, true, 2, 9, 1, 4, 4096);
        assertSame(record, record.copy(), "a record is already kept");
        assertEquals(4, record.layerNanos());
    }
}
