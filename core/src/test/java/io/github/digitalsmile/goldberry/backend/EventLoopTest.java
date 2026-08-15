package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// The rule that nothing but the UI runs on the UI thread, and that background
/// work still gets back safely.
class EventLoopTest {

    private static final WindowSpec SPEC =
            WindowSpec.of("Goldberry", LogicalSize.of(800f, 600f));

    private HeadlessBackend backend;
    private EventLoop loop;

    /// Released when a test finishes, so the watchdog can stand down.
    private CountDownLatch finished;

    private Thread watchdog;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend(new DisplayScale(1.5f));
        loop = new EventLoop(backend);
        finished = new CountDownLatch(1);

        // A test whose loop never stops would hang the build, not fail it:
        // JUnit's @Timeout defaults to the same thread and cannot interrupt an
        // infinite loop, so it is only noticed after the method returns -- which
        // never happens. This turns any such mistake into an ordinary assertion
        // failure a few seconds later.
        watchdog = new Thread(() -> {
            try {
                if (!finished.await(20, TimeUnit.SECONDS)) {
                    loop.stop();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "event-loop-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        finished.countDown();
        watchdog.join(TimeUnit.SECONDS.toMillis(5));
        loop.close();
        backend.close();
    }

    @Test
    @Timeout(10)
    @DisplayName("the loop ends when the last window closes")
    void endsWithTheLastWindow() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        window.requestClose();

        loop.run(event -> {
            if (event instanceof BackendEvent.CloseRequested request) {
                request.window().close();
            }
        });

        assertFalse(loop.isRunning());
        assertEquals(List.of(), backend.windows());
    }

    @Test
    @Timeout(10)
    @DisplayName("stop() ends the loop from the UI thread")
    void stopsFromUiThread() {
        backend.createWindow(SPEC);
        ((HeadlessWindow) backend.windows().getFirst()).expose();

        loop.run(event -> loop.stop());

        assertFalse(loop.isRunning());
    }

    @Test
    @Timeout(10)
    @DisplayName("stop() ends the loop from another thread")
    void stopsFromAnotherThread() throws Exception {
        backend.createWindow(SPEC);
        var stopper = new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // The loop is parked in pumpEvents. stop() has to reach it, which is
            // exactly what wakeup() is for.
            loop.stop();
        }, "stopper");

        stopper.start();
        loop.run(event -> {});
        stopper.join();

        assertFalse(loop.isRunning());
    }

    @Test
    @Timeout(10)
    @DisplayName("work posted from another thread runs on the UI thread")
    void postedWorkRunsOnUiThread() throws Exception {
        backend.createWindow(SPEC);
        var ranOn = new AtomicReference<Thread>();
        var uiThread = Thread.currentThread();

        var poster = new Thread(() -> loop.ui().execute(() -> {
            ranOn.set(Thread.currentThread());
            loop.stop();
        }), "poster");
        poster.start();

        loop.run(event -> {});
        poster.join();

        assertSame(uiThread, ranOn.get(), "queued work must run on the UI thread, not the poster's");
    }

    @Test
    @Timeout(10)
    @DisplayName("background work completes its future on the UI thread")
    void asyncCompletesOnUiThread() throws Exception {
        backend.createWindow(SPEC);
        var uiThread = Thread.currentThread();
        var workRanOn = new AtomicReference<Thread>();
        var callbackRanOn = new AtomicReference<Thread>();

        loop.supplyAsync(() -> {
                    workRanOn.set(Thread.currentThread());
                    return 42;
                })
                .thenAccept(value -> {
                    callbackRanOn.set(Thread.currentThread());
                    assertEquals(42, value);
                    loop.stop();
                });

        loop.run(event -> {});

        // The whole point: the work is off the UI thread, and its result is on it,
        // with no hand-off written by the caller.
        assertNotNull(workRanOn.get());
        assertFalse(workRanOn.get() == uiThread, "the work itself must not run on the UI thread");
        assertTrue(workRanOn.get().isVirtual(), "background work runs on a virtual thread");
        assertSame(uiThread, callbackRanOn.get(), "the callback must be on the UI thread");
    }

    @Test
    @Timeout(10)
    @DisplayName("a failing background task arrives as a failed future, not a lost stack trace")
    void asyncFailurePropagates() {
        backend.createWindow(SPEC);
        var boom = new IllegalStateException("could not load");
        var caught = new AtomicReference<Throwable>();

        loop.supplyAsync(() -> {
                    throw boom;
                })
                .whenComplete((value, error) -> {
                    caught.set(error);
                    loop.stop();
                });

        loop.run(event -> {});

        // Unwrapped: whenComplete on the future itself reports the exception it
        // was completed with. Only derived stages wrap in CompletionException.
        assertNotNull(caught.get());
        assertSame(boom, caught.get());
    }

    @Test
    @Timeout(20)
    @DisplayName("a slow background task does not hold up the event loop")
    void slowWorkDoesNotBlockTheLoop() throws Exception {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var eventsSeen = new ArrayList<BackendEvent>();

        loop.runAsync(() -> {
            started.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS), "the background task never started");

        // The task is still parked. Events must keep flowing regardless.
        window.expose();
        loop.run(event -> {
            eventsSeen.add(event);
            loop.stop();
        });
        release.countDown();

        assertEquals(1, eventsSeen.size(), "the loop kept working while the task was blocked");
    }

    @Test
    @Timeout(10)
    @DisplayName("running twice at once is refused")
    void refusesReentrantRun() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        // The sink is the only place that can call stop() here, so there has to
        // be an event to deliver -- otherwise the loop idles forever and JUnit's
        // same-thread @Timeout cannot interrupt it.
        window.expose();

        loop.run(event -> {
            assertThrows(BackendException.class, () -> loop.run(other -> {}));
            loop.stop();
        });
    }

    @Test
    @DisplayName("run() refuses another thread")
    void runRefusesOtherThreads() throws Exception {
        var failure = new AtomicReference<Throwable>();
        var other = new Thread(
                () -> failure.set(assertThrows(BackendException.class, () -> loop.run(event -> {}))),
                "off-ui");
        other.start();
        other.join();

        assertNotNull(failure.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("close() does not wait for background work to finish")
    void closeDoesNotWait() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        loop.runAsync(() -> {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        // An application that cannot exit because a download is still running is
        // the failure this avoids.
        loop.close();
        release.countDown();
    }

    @Test
    @DisplayName("a future completed after the loop stops is not lost, just late")
    void completionAfterStopIsQueued() throws ExecutionException, InterruptedException, Exception {
        backend.createWindow(SPEC);
        var future = loop.supplyAsync(() -> "done");

        // Nothing is draining the queue yet, so the completion is waiting.
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (loop.ui().pendingTaskCount() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5);
        }

        assertEquals(1, loop.ui().pendingTaskCount());
        loop.ui().drain();
        assertEquals("done", future.get());
    }
}
