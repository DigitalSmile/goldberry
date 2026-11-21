package dev.goldberry.render.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.render.BackendException;
import dev.goldberry.render.backend.headless.HeadlessBackend;

/// [EventLoop#turnWhileHeld]: what the loop does while the platform holds the
/// UI thread for a drag out, on a clock this test owns.
@DisplayName("a turn of the loop while the platform holds the thread")
@Timeout(10)
class EventLoopHeldTest {

    private HeadlessBackend backend;
    private TestClock clock;
    private EventLoop loop;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        clock = new TestClock();
        loop = clock.loopOver(backend);
    }

    @AfterEach
    void tearDown() {
        loop.close();
        backend.close();
    }

    @Test
    @DisplayName("runs posted work and due timers, and what they post in turn")
    void runs() {
        var ran = new ArrayList<String>();
        loop.ui().execute(() -> ran.add("posted"));
        loop.after(Duration.ofMillis(5), () -> {
            ran.add("timer");
            loop.ui().execute(() -> ran.add("posted by the timer"));
        });
        loop.after(Duration.ofMillis(50), () -> ran.add("not yet"));
        clock.advance(Duration.ofMillis(5));

        loop.turnWhileHeld();

        assertEquals(List.of("posted", "timer", "posted by the timer"), ran);
    }

    @Test
    @DisplayName("does not nest: a turn asked for inside one returns at once")
    void noNesting() {
        var depth = new int[] {0, 0};
        loop.ui().execute(() -> {
            depth[0]++;
            depth[1] = Math.max(depth[1], depth[0]);
            loop.ui().execute(() -> {});
            loop.turnWhileHeld();
            depth[0]--;
        });

        loop.turnWhileHeld();

        assertEquals(1, depth[1]);
    }

    @Test
    @DisplayName("is the UI thread's alone")
    void uiThreadOnly() throws InterruptedException {
        var failure = new Throwable[1];
        var other = Thread.ofPlatform().start(() -> {
            try {
                loop.turnWhileHeld();
            } catch (BackendException e) {
                failure[0] = e;
            }
        });
        other.join();

        assertThrows(BackendException.class, () -> {
            if (failure[0] instanceof BackendException e) {
                throw e;
            }
        });
    }
}
