package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.RendererRequirement;

/// A platform callback that arrives off the UI thread is run on it, by the next
/// pump, rather than beside it: what a tray row's handler is routed through.
@DisplayName("work handed over to the UI thread")
class HandedOverTest {

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("runs at once when it is already on the UI thread")
    void onTheUiThreadAtOnce() {
        withBackend(backend -> {
            var ran = new ArrayList<Thread>();

            backend.onUiThread(() -> ran.add(Thread.currentThread()));

            assertEquals(List.of(Thread.currentThread()), ran);
        });
    }

    @Test
    @Timeout(10)
    @DisplayName("from another thread, waits for the pump, wakes it, and runs on the UI thread")
    void fromAnotherThread() {
        withBackend(backend -> {
            var ran = new CopyOnWriteArrayList<Thread>();
            var other = Thread.ofVirtual().start(() -> backend.onUiThread(() -> ran.add(Thread.currentThread())));
            try {
                other.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            assertTrue(ran.isEmpty(), "not beside the UI thread, and not before its pump");

            // A long wait that the hand-over's wakeup cuts short.
            var delivered = backend.pumpEvents(event -> {}, Duration.ofSeconds(5));

            assertEquals(1, ran.size());
            assertSame(Thread.currentThread(), ran.getFirst());
            assertTrue(delivered >= 1, "counted with what the pump delivered");
        });
    }

    @Test
    @DisplayName("a handed-over failure is logged, and the rest still run")
    void aFailureDoesNotStopTheRest() {
        withBackend(backend -> {
            var ran = new CopyOnWriteArrayList<String>();
            var other = Thread.ofVirtual().start(() -> {
                backend.onUiThread(() -> {
                    throw new IllegalStateException("the handler's own bug");
                });
                backend.onUiThread(() -> ran.add("second"));
            });
            try {
                other.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }

            backend.pumpEvents(event -> {}, Duration.ZERO);

            assertEquals(List.of("second"), ran);
        });
    }

    private static void withBackend(Consumer<Sdl3Backend> body) {
        var previousDriver = System.getProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, "dummy");
        try (var backend = new Sdl3Backend()) {
            body.accept(backend);
        } finally {
            if (previousDriver == null) {
                System.clearProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
            } else {
                System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, previousDriver);
            }
        }
    }
}
