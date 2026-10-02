package dev.goldberry.media.engine;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

/// Prepared pictures waiting for their time, and the one being shown.
///
/// The video decode thread prepares each picture in a [Slot] it [#obtain]s, and
/// [#put]s it here: converted to BGRA, or its planes copied, as the slot's
/// [Shape] says ([PictureForm]). Whoever presents calls
/// [#present] with the master clock: the newest picture whose time has come
/// becomes the one shown, and the ones it passed are dropped. The view calls it
/// on each frame it paints, and the decode thread calls it too while it waits
/// for room, so a player with no view on screen still moves through its
/// pictures and reaches its end.
///
/// ## The pictures are reused
///
/// A 1080p picture is 8 MB, and 30 of them a second would be a quarter of a
/// gigabyte of garbage a second. So there are at most [#MAX_PICTURES] buffers,
/// and a picture that has been passed is handed back to the pool. Each buffer
/// has one [Shape], and one of another shape than the next picture's is left to
/// the collector, as when a stream changes size or the form changes. A picture
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
/// picture prepared for an older Serial is refused by [#put]. The picture being
/// shown stays on screen until the first picture of the new position replaces
/// it, which it does whatever its time: a seek shows its target at once rather
/// than a frame of black.
///
/// ## Handed from one video thread to the next
///
/// A switch of video track retires the video thread and starts another on
/// the same queue. [#releaseWaiters()] ends the retiring thread's wait in
/// [#obtain] without [#abort()]ing the queue, which is for good. The picture
/// shown stays up through the switch, and the seek that follows it flushes what
/// the old track queued, so the new track's first picture replaces the old one's
/// last, whatever their sizes.
final class FrameQueue {

    /// How many prepared pictures may wait for their time.
    static final int CAPACITY = 3;

    /// How many handed-out pictures are kept from reuse.
    static final int HANDED_OUT = 2;

    /// The most buffers there are: the queue, the handed-out ones, the one shown,
    /// and the one being prepared.
    static final int MAX_PICTURES = CAPACITY + HANDED_OUT + 2;

    /// Rows start on this boundary, which swscale's vector paths like, and so
    /// do the planes of a [PictureForm#PLANES] slot.
    private static final int ALIGN = 64;

    /// What a slot holds: a `width × height` picture converted to BGRA, or with
    /// the planes of `planes`, the frame contract's layout.
    ///
    /// @param planes the plane layout, or null for premultiplied BGRA
    record Shape(@Nullable PixelFormat planes, int width, int height) {

        Shape {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("size " + width + "×" + height);
            }
        }

        /// A picture converted to BGRA.
        static Shape converted(int width, int height) {
            return new Shape(null, width, height);
        }

        /// What `frame` becomes in `form`: its size, and its planes' layout for
        /// [PictureForm#PLANES].
        static Shape of(VideoFrame frame, PictureForm form) {
            return new Shape(form == PictureForm.PLANES ? frame.format() : null, frame.width(), frame.height());
        }

        /// The form a picture of this shape is handed out in.
        PictureForm form() {
            return planes == null ? PictureForm.CONVERTED : PictureForm.PLANES;
        }
    }

    /// One reusable picture buffer, and what it holds now.
    ///
    /// One direct buffer holds every plane, each from an offset of its own with
    /// rows [#ALIGN]ed; a converted picture is one plane of BGRA. Written by the
    /// video thread alone, between [#obtain] and [#put].
    static final class Slot {
        private final Shape shape;
        private final List<Integer> strides;
        private final List<ByteBuffer> planes;
        private final List<MemorySegment> segments;
        private VideoFrame.ColorMatrix matrix = VideoFrame.ColorMatrix.BT709;
        private boolean fullRange;
        private @Nullable Picture picture;
        /// Whether the picture it holds has been handed out to a view.
        private boolean handedOutOnce;

        Slot(Shape shape) {
            this.shape = shape;
            var layout = shape.planes();
            var count = layout == null ? 1 : layout.planes();
            var strides = new ArrayList<Integer>(count);
            var sizes = new long[count];
            var total = 0L;
            for (var plane = 0; plane < count; plane++) {
                var stride = aligned(rowBytes(plane));
                strides.add(stride);
                sizes[plane] = Math.multiplyExact((long) stride, rows(plane));
                total += sizes[plane];
            }
            var buffer = ByteBuffer.allocateDirect(Math.toIntExact(total));
            var planes = new ArrayList<ByteBuffer>(count);
            var segments = new ArrayList<MemorySegment>(count);
            var offset = 0;
            for (var plane = 0; plane < count; plane++) {
                var slice = buffer.slice(offset, (int) sizes[plane]);
                planes.add(slice);
                segments.add(MemorySegment.ofBuffer(slice));
                offset += (int) sizes[plane];
            }
            this.strides = List.copyOf(strides);
            this.planes = List.copyOf(planes);
            this.segments = List.copyOf(segments);
        }

        private static int aligned(int bytes) {
            return (bytes + ALIGN - 1) / ALIGN * ALIGN;
        }

        private int rowBytes(int plane) {
            var layout = shape.planes();
            return layout == null ? Math.multiplyExact(shape.width(), 4) : layout.planeRowBytes(plane, shape.width());
        }

        private int rows(int plane) {
            var layout = shape.planes();
            return layout == null ? shape.height() : layout.planeRows(plane, shape.height());
        }

        /// What this slot holds.
        Shape shape() {
            return shape;
        }

        /// Where plane `index` is written: the converter's BGRA for a converted
        /// slot, which has one plane.
        MemorySegment segment(int index) {
            return segments.get(index);
        }

        /// Bytes from one row of plane `index` to the next.
        int stride(int index) {
            return strides.get(index);
        }

        /// Records the colour the picture was encoded with, which a
        /// [PictureForm#PLANES] picture carries to its shader.
        void colour(VideoFrame.ColorMatrix matrix, boolean fullRange) {
            this.matrix = Objects.requireNonNull(matrix, "matrix");
            this.fullRange = fullRange;
        }

        /// Copies `frame`'s planes into this slot's, row by row, and its colour:
        /// the [PictureForm#PLANES] preparation, which costs a copy where
        /// converting would cost a pass of swscale.
        ///
        /// @throws IllegalArgumentException when `frame` is not of this slot's
        ///                                  shape
        void copyPlanes(VideoFrame frame) {
            var layout = shape.planes();
            if (layout == null || !Shape.of(frame, PictureForm.PLANES).equals(shape)) {
                throw new IllegalArgumentException("a " + frame.format() + " " + frame.width() + "×" + frame.height()
                        + " picture does not fit " + shape);
            }
            for (var plane = 0; plane < layout.planes(); plane++) {
                var source = frame.planes().get(plane);
                int sourceStride = frame.strides().get(plane);
                var target = segments.get(plane);
                int targetStride = strides.get(plane);
                var rowBytes = rowBytes(plane);
                var rows = rows(plane);
                if (sourceStride == targetStride) {
                    MemorySegment.copy(source, 0, target, 0, (long) sourceStride * (rows - 1) + rowBytes);
                } else {
                    for (var row = 0; row < rows; row++) {
                        MemorySegment.copy(
                                source, (long) row * sourceStride, target, (long) row * targetStride, rowBytes);
                    }
                }
            }
            colour(frame.matrix(), frame.fullRange());
        }

        boolean fits(Shape wanted) {
            return shape.equals(wanted);
        }

        long ptsNanos() {
            var current = picture;
            return current == null ? Long.MIN_VALUE : current.ptsNanos();
        }

        /// The picture this slot holds, presented at `ptsNanos`.
        private Picture picture(long ptsNanos) {
            var layout = shape.planes();
            return layout == null
                    ? new VideoPicture(shape.width(), shape.height(), strides.getFirst(), planes.getFirst(), ptsNanos)
                    : new VideoPlanes(
                            layout, shape.width(), shape.height(), planes, strides, matrix, fullRange, ptsNanos);
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
    /// Pictures handed out to a view, each once; and pictures the clock moved
    /// past before any view was handed them. A flush's pictures are neither.
    private long shownCount;
    private long passedCount;

    /// A buffer for a picture of `shape` and `forSerial`, waiting while the queue
    /// is full or every buffer is in use. While it waits it presents against
    /// `clock` itself, so the queue drains with no view to drain it.
    ///
    /// @return the buffer, or null when the queue was aborted, or flushed to a
    ///         newer Serial, or its waiters released, while this waited
    @Nullable
    Slot obtain(Shape shape, int forSerial, LongSupplier clock) {
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
                        if (slot.fits(shape)) {
                            return slot;
                        }
                        // The stream changed size, or the form changed: this
                        // buffer is the wrong one, and the collector has it.
                        created--;
                        continue;
                    }
                    if (created < MAX_PICTURES) {
                        created++;
                        return new Slot(shape);
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

    /// Queues `slot`, prepared, as the picture presented at `ptsNanos`.
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
            slot.picture = slot.picture(ptsNanos);
            slot.handedOutOnce = false;
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
    /// @return the picture, in the form it was prepared in, or null when nothing
    ///         has been decoded yet
    @Nullable
    Picture present(long clockNanos, boolean handOut) {
        lock.lock();
        try {
            var picture = presentLocked(clockNanos, handOut);
            changed.signalAll();
            return picture;
        } finally {
            lock.unlock();
        }
    }

    private @Nullable Picture presentLocked(long clockNanos, boolean handOut) {
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
        if (handOut && !current.handedOutOnce) {
            current.handedOutOnce = true;
            shownCount++;
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
        if (previous != null && previous != next && !previous.handedOutOnce && !shownStale) {
            // Its time came and went between two views' asks: a picture no one saw.
            passedCount++;
        }
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

    /// How many pictures have been handed out to a view, each counted once.
    long shownCount() {
        lock.lock();
        try {
            return shownCount;
        } finally {
            lock.unlock();
        }
    }

    /// How many pictures were queued, fell due, and were passed over for a newer
    /// one before any view was handed them: pictures played and never seen.
    long passedCount() {
        lock.lock();
        try {
            return passedCount;
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
