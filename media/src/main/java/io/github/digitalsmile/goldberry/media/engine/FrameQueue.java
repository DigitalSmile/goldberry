package io.github.digitalsmile.goldberry.media.engine;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.VideoPicture;

/// Converted pictures waiting for their time, and the one being shown
/// (`docs/goldberry-media.md` §1, "Frame queue", and §3, "Presentation").
///
/// The video decode thread converts each picture into a [Slot] it [#obtain]s,
/// and [#put]s it here. Whoever presents calls [#present] with the master clock:
/// the newest picture whose time has come becomes the one shown, and the ones it
/// passed are dropped. The view calls it on each frame it paints, and the decode
/// thread calls it too while it waits for room, so a player with no view on
/// screen still moves through its pictures and reaches its end.
///
/// ## The pictures are reused
///
/// A 1080p picture is 8 MB, and 30 of them a second would be a quarter of a
/// gigabyte of garbage a second. So there are at most [#MAX_PICTURES] buffers,
/// and a picture that has been passed is handed back to the pool. A picture
/// **handed out** to a view ([#present] with `handOut`) is not reused until
/// [#HANDED_OUT] newer ones have been handed out after it, which is what lets a
/// view draw the picture it asked for in the frame it asked in, even with two
/// views on one player. The buffers are direct `ByteBuffer`s, freed by the
/// collector: a view that holds a picture past its time sees newer pixels, and
/// never freed memory.
///
/// ## Serial
///
/// [#flush] takes the Serial of a seek. The queued pictures are dropped, and a
/// picture converted for an older Serial is refused by [#put]. The picture being
/// shown stays on screen until the first picture of the new position replaces
/// it, which it does whatever its time: a seek shows its target at once rather
/// than a frame of black.
///
/// ## Handed from one video thread to the next
///
/// A switch of video track (§6) retires the video thread and starts another on
/// the same queue. [#releaseWaiters()] ends the retiring thread's wait in
/// [#obtain] without [#abort()]ing the queue, which is for good. The picture
/// shown stays up through the switch, and the seek that follows it flushes what
/// the old track queued, so the new track's first picture replaces the old one's
/// last, whatever their sizes.
final class FrameQueue {

    /// How many converted pictures may wait for their time.
    static final int CAPACITY = 3;

    /// How many handed-out pictures are kept from reuse.
    static final int HANDED_OUT = 2;

    /// The most buffers there are: the queue, the handed-out ones, the one shown,
    /// and the one being converted.
    static final int MAX_PICTURES = CAPACITY + HANDED_OUT + 2;

    /// Rows start on this boundary, which swscale's vector paths like.
    private static final int ALIGN = 64;

    /// One reusable picture buffer, and what it holds now.
    static final class Slot {
        private final int width;
        private final int height;
        private final int stride;
        private final ByteBuffer buffer;
        private final MemorySegment segment;
        private @Nullable VideoPicture picture;

        Slot(int width, int height) {
            this.width = width;
            this.height = height;
            this.stride = (width * 4 + ALIGN - 1) / ALIGN * ALIGN;
            this.buffer = ByteBuffer.allocateDirect(Math.multiplyExact(stride, height));
            this.segment = MemorySegment.ofBuffer(buffer);
        }

        /// Where the converter writes.
        MemorySegment segment() {
            return segment;
        }

        /// Bytes from one row to the next.
        int stride() {
            return stride;
        }

        boolean fits(int wantedWidth, int wantedHeight) {
            return width == wantedWidth && height == wantedHeight;
        }

        long ptsNanos() {
            var current = picture;
            return current == null ? Long.MIN_VALUE : current.ptsNanos();
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final ArrayDeque<Slot> queue = new ArrayDeque<>();
    private final ArrayDeque<Slot> free = new ArrayDeque<>();
    private final ArrayDeque<Slot> handedOut = new ArrayDeque<>();
    private @Nullable Slot shown;
    private boolean shownStale;
    private int serial;
    private int created;
    private boolean aborted;
    /// Counts [#releaseWaiters()] calls: a waiting [#obtain] that sees it move
    /// gives up.
    private int releases;

    /// A buffer for a `width × height` picture of `forSerial`, waiting while the
    /// queue is full or every buffer is in use. While it waits it presents against
    /// `clock` itself, so the queue drains with no view to drain it.
    ///
    /// @return the buffer, or null when the queue was aborted, or flushed to a
    ///         newer Serial, or its waiters released, while this waited
    @Nullable
    Slot obtain(int width, int height, int forSerial, LongSupplier clock) {
        lock.lock();
        try {
            var released = releases;
            while (true) {
                if (aborted || forSerial != serial || released != releases) {
                    return null;
                }
                if (queue.size() < CAPACITY) {
                    var slot = free.pollFirst();
                    if (slot != null) {
                        if (slot.fits(width, height)) {
                            return slot;
                        }
                        // The stream changed size: this buffer is the wrong one,
                        // and the collector has it.
                        created--;
                        continue;
                    }
                    if (created < MAX_PICTURES) {
                        created++;
                        return new Slot(width, height);
                    }
                }
                // No room: move the queue on by the clock, as a view would, and look
                // again when something changes or in 10 ms, whichever is first.
                presentLocked(clock.getAsLong(), false);
                var signalled = changed.awaitNanos(TimeUnit.MILLISECONDS.toNanos(10)) > 0;
                if (!signalled && aborted) {
                    return null;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    /// Queues `slot`, converted, as the picture presented at `ptsNanos`.
    ///
    /// @return false when the picture is stale (a newer Serial) or the queue was
    ///         aborted; the buffer goes back to the pool either way
    boolean put(Slot slot, long ptsNanos, int forSerial) {
        lock.lock();
        try {
            if (aborted || forSerial != serial) {
                free.addLast(slot);
                changed.signalAll();
                return false;
            }
            slot.picture = new VideoPicture(slot.width, slot.height, slot.stride, slot.buffer, ptsNanos);
            queue.addLast(slot);
            changed.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /// Gives back a buffer that was obtained and not queued.
    void recycle(Slot slot) {
        lock.lock();
        try {
            free.addLast(slot);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// The picture to show at `clockNanos`: the newest one whose time has come,
    /// or the first after a seek whatever its time, or the one already shown.
    ///
    /// @param handOut whether the caller will draw it, and so whether it is kept
    ///                from reuse until two newer ones have been handed out
    /// @return the picture, or null when nothing has been decoded yet
    @Nullable
    VideoPicture present(long clockNanos, boolean handOut) {
        lock.lock();
        try {
            var picture = presentLocked(clockNanos, handOut);
            changed.signalAll();
            return picture;
        } finally {
            lock.unlock();
        }
    }

    private @Nullable VideoPicture presentLocked(long clockNanos, boolean handOut) {
        if ((shown == null || shownStale) && !queue.isEmpty()) {
            advanceTo(Objects.requireNonNull(queue.pollFirst()));
            shownStale = false;
        }
        for (var next = queue.peekFirst(); next != null && next.ptsNanos() <= clockNanos; next = queue.peekFirst()) {
            advanceTo(Objects.requireNonNull(queue.pollFirst()));
        }
        var current = shown;
        if (current == null) {
            return null;
        }
        if (handOut && handedOut.peekLast() != current) {
            handedOut.remove(current);
            handedOut.addLast(current);
            while (handedOut.size() > HANDED_OUT) {
                var old = handedOut.pollFirst();
                if (old != shown && !queue.contains(old)) {
                    free.addLast(old);
                }
            }
        }
        return current.picture;
    }

    private void advanceTo(Slot next) {
        var previous = shown;
        shown = next;
        if (previous != null && previous != next && !handedOut.contains(previous)) {
            free.addLast(previous);
        }
    }

    /// Drops the queued pictures and moves to `newSerial`. The picture shown stays
    /// until the first of the new position replaces it.
    void flush(int newSerial) {
        lock.lock();
        try {
            serial = newSerial;
            free.addAll(queue);
            queue.clear();
            shownStale = shown != null;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Whether no picture is waiting for its time.
    boolean drained() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /// How many pictures wait for their time.
    int queued() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /// The time of the first waiting picture that is still in the future at
    /// `clockNanos`, or [Long#MIN_VALUE] when none is. The ones already due are
    /// skipped: the next [#present] shows them, and a caller asking when to present
    /// again wants the one after.
    long nextPtsAfter(long clockNanos) {
        lock.lock();
        try {
            for (var slot : queue) {
                if (slot.ptsNanos() > clockNanos) {
                    return slot.ptsNanos();
                }
            }
            return Long.MIN_VALUE;
        } finally {
            lock.unlock();
        }
    }

    /// The time of the picture shown, or [Long#MIN_VALUE] before the first.
    long shownPtsNanos() {
        lock.lock();
        try {
            return shown == null ? Long.MIN_VALUE : shown.ptsNanos();
        } finally {
            lock.unlock();
        }
    }

    /// Ends every [#obtain] waiting now, which returns null, and leaves the queue
    /// working: the thread that waited is being retired, and the next one uses the
    /// queue as it is. A later [#obtain] waits as before.
    void releaseWaiters() {
        lock.lock();
        try {
            releases++;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Wakes a waiting [#obtain] for good. What has been shown stays, so a paused
    /// player's view keeps its last picture after the player is closed.
    void abort() {
        lock.lock();
        try {
            aborted = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
