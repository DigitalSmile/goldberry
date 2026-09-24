package io.github.digitalsmile.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The frame queue's rules with no FFmpeg: a full queue makes [FrameQueue#obtain]
/// wait, and the three ways to end that wait. Only [FrameQueue#releaseWaiters()]
/// leaves the queue working, which is what lets a video track switch hand it from
/// one video thread to the next.
@DisplayName("FrameQueue")
class FrameQueueTest {

    private static final long SECOND = 1_000_000_000L;

    /// A clock stopped at zero: nothing queued in the future ever falls due.
    private static final LongSupplier STOPPED = () -> 0;

    /// Fills `frames` for Serial 0 with pictures a second apart from one second,
    /// so the first is shown and [FrameQueue#CAPACITY] wait: the next `obtain`
    /// has no room.
    private static void fill(FrameQueue frames) {
        for (var i = 0; i <= FrameQueue.CAPACITY; i++) {
            var slot = frames.obtain(4, 2, 0, STOPPED);
            assertNotNull(slot, "room for picture " + i);
            assertTrue(frames.put(slot, (i + 1) * SECOND, 0));
        }
        assertEquals(FrameQueue.CAPACITY, frames.queued());
    }

    /// An `obtain` on another thread, which a full queue keeps waiting.
    private static CompletableFuture<FrameQueue.Slot> waitingObtain(FrameQueue frames) {
        var result = CompletableFuture.supplyAsync(
                () -> frames.obtain(4, 2, 0, STOPPED),
                runnable -> Thread.ofVirtual().start(runnable));
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(result.isDone(), "a full queue gives no buffer");
        return result;
    }

    @Test
    @DisplayName("releasing the waiters ends a waiting obtain with null, and the queue goes on working")
    void releaseWaiters() throws Exception {
        var frames = new FrameQueue();
        fill(frames);
        var waiting = waitingObtain(frames);

        frames.releaseWaiters();
        assertNull(waiting.get(5, TimeUnit.SECONDS));

        // Not aborted: the picture shown stays, and after a flush the next thread
        // gets buffers as the first did.
        assertEquals(SECOND, frames.shownPtsNanos());
        frames.flush(1);
        assertTrue(frames.drained());
        var slot = frames.obtain(4, 2, 1, STOPPED);
        assertNotNull(slot);
        assertTrue(frames.put(slot, 2 * SECOND, 1));
        assertEquals(2 * SECOND, frames.present(0, false).ptsNanos(), "a flush's first picture shows at once");
    }

    @Test
    @DisplayName("a release before an obtain does not end that obtain's wait")
    void releaseIsNotSticky() throws Exception {
        var frames = new FrameQueue();
        fill(frames);
        frames.releaseWaiters();
        var waiting = waitingObtain(frames);
        frames.abort();
        assertNull(waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("a flush to a newer Serial ends a waiting obtain for the older one")
    void flushEndsAStaleWait() throws Exception {
        var frames = new FrameQueue();
        fill(frames);
        var waiting = waitingObtain(frames);
        frames.flush(1);
        assertNull(waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("after an abort nothing more is obtained")
    void abortIsForGood() {
        var frames = new FrameQueue();
        frames.abort();
        assertNull(frames.obtain(4, 2, 0, STOPPED));
    }

    @Test
    @DisplayName("a buffer of another size is replaced, not reused")
    void resizes() {
        var frames = new FrameQueue();
        var small = frames.obtain(4, 2, 0, STOPPED);
        assertNotNull(small);
        frames.recycle(small);
        var large = frames.obtain(8, 4, 0, STOPPED);
        assertNotNull(large);
        assertTrue(large.fits(8, 4));
        assertFalse(large.fits(4, 2));
    }
}
