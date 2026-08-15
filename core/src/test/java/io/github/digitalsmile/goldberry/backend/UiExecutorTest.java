package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class UiExecutorTest {

    @Test
    @DisplayName("tasks run in the order they were posted")
    void runsInOrder() {
        var executor = new UiExecutor(() -> {});
        var order = new ArrayList<Integer>();

        for (var i = 0; i < 5; i++) {
            var n = i;
            executor.execute(() -> order.add(n));
        }

        assertEquals(5, executor.drain());
        assertEquals(List.of(0, 1, 2, 3, 4), order);
    }

    @Test
    @DisplayName("posting wakes the loop, and enqueues before it does")
    void wakesAfterEnqueueing() {
        // The other order races: the loop could wake, find an empty queue, and
        // park again before the task lands.
        var seenAtWakeup = new AtomicInteger(-1);
        var executor = new UiExecutor[1];
        executor[0] = new UiExecutor(() -> seenAtWakeup.set(executor[0].pendingTaskCount()));

        executor[0].execute(() -> {});

        assertEquals(1, seenAtWakeup.get(), "the task must be queued before the wakeup fires");
    }

    @Test
    @DisplayName("a task posted while draining waits for the next drain")
    void selfPostingTaskDefers() {
        var executor = new UiExecutor(() -> {});
        var runs = new AtomicInteger();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
                if (runs.get() < 3) {
                    executor.execute(this);
                }
            }
        });

        // One drain runs one generation. A self-scheduling animation must not be
        // able to hold the loop and starve events.
        assertEquals(1, executor.drain());
        assertEquals(1, runs.get());
        assertEquals(1, executor.drain());
        assertEquals(2, runs.get());
    }

    @Test
    @DisplayName("one failing task does not swallow the rest of the batch")
    void failureDoesNotStopTheBatch() {
        var executor = new UiExecutor(() -> {});
        var ran = new ArrayList<String>();

        executor.execute(() -> ran.add("first"));
        executor.execute(() -> {
            throw new IllegalStateException("handler failed");
        });
        executor.execute(() -> ran.add("third"));

        var thrown = assertThrows(BackendException.class, executor::drain);

        assertEquals(List.of("first", "third"), ran);
        assertInstanceOfIllegalState(thrown);
    }

    @Test
    @DisplayName("several failures are reported together")
    void collectsMultipleFailures() {
        var executor = new UiExecutor(() -> {});
        executor.execute(() -> {
            throw new IllegalStateException("one");
        });
        executor.execute(() -> {
            throw new IllegalStateException("two");
        });

        var thrown = assertThrows(BackendException.class, executor::drain);

        assertEquals(1, thrown.getSuppressed().length, "the second failure is suppressed, not lost");
    }

    @Test
    @Timeout(10)
    @DisplayName("posting is safe from any thread")
    void postingIsThreadSafe() throws Exception {
        var executor = new UiExecutor(() -> {});
        var threads = 8;
        var perThread = 100;
        var ready = new CountDownLatch(threads);
        var count = new AtomicInteger();

        var workers = new ArrayList<Thread>();
        for (var t = 0; t < threads; t++) {
            var worker = new Thread(() -> {
                for (var i = 0; i < perThread; i++) {
                    executor.execute(count::incrementAndGet);
                }
                ready.countDown();
            }, "poster-" + t);
            workers.add(worker);
            worker.start();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        for (var worker : workers) {
            worker.join();
        }

        assertEquals(threads * perThread, executor.drain());
        assertEquals(threads * perThread, count.get());
    }

    @Test
    @DisplayName("draining from another thread is refused")
    void drainRefusesOtherThreads() throws Exception {
        var executor = new UiExecutor(() -> {});
        var failed = new boolean[1];

        var other = new Thread(() -> {
            assertThrows(BackendException.class, executor::drain);
            failed[0] = true;
        }, "off-ui");
        other.start();
        other.join();

        assertTrue(failed[0]);
    }

    @Test
    @DisplayName("the UI thread recognises itself")
    void identifiesTheUiThread() throws Exception {
        var executor = new UiExecutor(() -> {});
        var offUi = new boolean[1];

        assertTrue(executor.isUiThread());
        var other = new Thread(() -> offUi[0] = executor.isUiThread(), "off-ui");
        other.start();
        other.join();

        assertFalse(offUi[0]);
    }

    private static void assertInstanceOfIllegalState(BackendException thrown) {
        assertSame(IllegalStateException.class, thrown.getCause().getClass());
    }
}
