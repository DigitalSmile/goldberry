package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

/// The frame queue's rules with no FFmpeg: a full queue makes [FrameQueue#obtain]
/// wait, and the three ways to end that wait. Only [FrameQueue#releaseWaiters()]
/// leaves the queue working, which is what lets a video track switch hand it from
/// one video thread to the next. And its slots' two forms (`docs/gpu-plan.md`,
/// D8): converted BGRA, and planes copied as a decoder lent them.
@DisplayName("FrameQueue")
class FrameQueueTest {

    private static final long SECOND = 1_000_000_000L;

    /// A clock stopped at zero: nothing queued in the future ever falls due.
    private static final LongSupplier STOPPED = () -> 0;

    /// A tiny converted picture: what the queue's rules need, and no more.
    private static final FrameQueue.Shape SMALL = FrameQueue.Shape.converted(4, 2);

    /// Fills `frames` for Serial 0 with pictures a second apart from one second,
    /// so the first is shown and [FrameQueue#CAPACITY] wait: the next `obtain`
    /// has no room.
    private static void fill(FrameQueue frames) {
        for (var i = 0; i <= FrameQueue.CAPACITY; i++) {
            var slot = frames.obtain(SMALL, 0, STOPPED);
            assertNotNull(slot, "room for picture " + i);
            assertTrue(frames.put(slot, (i + 1) * SECOND, 0));
        }
        assertEquals(FrameQueue.CAPACITY, frames.queued());
    }

    /// An `obtain` on another thread, which a full queue keeps waiting.
    private static CompletableFuture<FrameQueue.Slot> waitingObtain(FrameQueue frames) {
        var result = CompletableFuture.supplyAsync(
                () -> frames.obtain(SMALL, 0, STOPPED),
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
        var slot = frames.obtain(SMALL, 1, STOPPED);
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
        assertNull(frames.obtain(SMALL, 0, STOPPED));
    }

    @Test
    @DisplayName("a buffer of another size is replaced, not reused")
    void resizes() {
        var frames = new FrameQueue();
        var small = frames.obtain(SMALL, 0, STOPPED);
        assertNotNull(small);
        frames.recycle(small);
        var large = frames.obtain(FrameQueue.Shape.converted(8, 4), 0, STOPPED);
        assertNotNull(large);
        assertTrue(large.fits(FrameQueue.Shape.converted(8, 4)));
        assertFalse(large.fits(SMALL));
    }

    // ------------------------------------------------------------ planes (D8)

    /// A `width × height` picture of `format` in native memory, each plane's rows
    /// `padding` bytes longer than they need be, every sample distinct: sample
    /// `i` of plane `p` holds `(p × 100 + i) & 0xff`, or for 16-bit samples
    /// `(p × 1000 + i) & 0x3ff`.
    private static VideoFrame frame(Arena arena, PixelFormat format, int width, int height, int padding) {
        var planes = new ArrayList<MemorySegment>();
        var strides = new ArrayList<Integer>();
        for (var plane = 0; plane < format.planes(); plane++) {
            var rowBytes = format.planeRowBytes(plane, width);
            var stride = rowBytes + padding;
            var rows = format.planeRows(plane, height);
            var segment = arena.allocate((long) stride * rows);
            var samples = rowBytes / format.bytesPerSample();
            for (var row = 0; row < rows; row++) {
                for (var column = 0; column < samples; column++) {
                    var i = row * samples + column;
                    if (format.bytesPerSample() == 1) {
                        segment.set(ValueLayout.JAVA_BYTE, (long) row * stride + column, (byte) (plane * 100 + i));
                    } else {
                        segment.set(ValueLayout.JAVA_SHORT_UNALIGNED, (long) row * stride + column * 2L, (short)
                                ((plane * 1000 + i) & 0x3ff));
                    }
                }
            }
            planes.add(segment);
            strides.add(stride);
        }
        return new VideoFrame(format, width, height, planes, strides, VideoFrame.ColorMatrix.BT2020, true, 40_000_000L);
    }

    /// Asserts that `planes` holds `frame`'s samples, each at its place.
    private static void assertSamples(VideoFrame frame, VideoPlanes planes) {
        var format = frame.format();
        for (var plane = 0; plane < format.planes(); plane++) {
            var samples = format.planeRowBytes(plane, frame.width()) / format.bytesPerSample();
            var rows = format.planeRows(plane, frame.height());
            for (var row = 0; row < rows; row++) {
                for (var column = 0; column < samples; column++) {
                    var i = row * samples + column;
                    var expected = format.bytesPerSample() == 1 ? (plane * 100 + i) & 0xff : (plane * 1000 + i) & 0x3ff;
                    assertEquals(
                            expected,
                            planes.sample(plane, column, row),
                            "plane " + plane + ", sample " + column + " of row " + row);
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} at {1}×{2}, rows padded by {3}")
    @CsvSource({
        // Odd sizes: the chroma planes round up.
        "NV12, 5, 3, 0",
        "NV12, 5, 3, 7",
        "I420, 7, 5, 3",
        "P010, 5, 3, 2",
        "I010, 3, 3, 0",
        // Rows of exactly the slot's 64-byte stride: one copy a plane.
        "I420, 64, 4, 0",
    })
    @DisplayName("a planes slot copies a picture's planes as they are, and hands them out with its colour")
    void copiesPlanes(PixelFormat format, int width, int height, int padding) {
        try (var arena = Arena.ofConfined()) {
            var frame = frame(arena, format, width, height, padding);
            var frames = new FrameQueue();
            var shape = FrameQueue.Shape.of(frame, PictureForm.PLANES);
            assertEquals(PictureForm.PLANES, shape.form());
            var slot = frames.obtain(shape, 0, STOPPED);
            assertNotNull(slot);
            for (var plane = 0; plane < format.planes(); plane++) {
                assertEquals(0, slot.stride(plane) % 64, "rows start on 64 bytes");
                assertTrue(slot.stride(plane) >= format.planeRowBytes(plane, width));
            }
            slot.copyPlanes(frame);
            assertTrue(frames.put(slot, frame.ptsNanos(), 0));

            var shown = assertInstanceOf(VideoPlanes.class, frames.present(0, true));
            assertEquals(format, shown.format());
            assertEquals(width, shown.width());
            assertEquals(height, shown.height());
            assertEquals(VideoFrame.ColorMatrix.BT2020, shown.matrix());
            assertTrue(shown.fullRange());
            assertEquals(frame.ptsNanos(), shown.ptsNanos());
            assertSamples(frame, shown);
        }
    }

    @Test
    @DisplayName("a converted slot hands out BGRA, and the frame's own shape is its size alone")
    void convertedSlot() {
        try (var arena = Arena.ofConfined()) {
            var frame = frame(arena, PixelFormat.I420, 6, 4, 0);
            var shape = FrameQueue.Shape.of(frame, PictureForm.CONVERTED);
            assertEquals(FrameQueue.Shape.converted(6, 4), shape);
            assertEquals(PictureForm.CONVERTED, shape.form());
            var frames = new FrameQueue();
            var slot = frames.obtain(shape, 0, STOPPED);
            assertNotNull(slot);
            assertEquals(64, slot.stride(0));
            assertTrue(frames.put(slot, 0, 0));
            var shown = assertInstanceOf(VideoPicture.class, frames.present(0, true));
            assertEquals(6, shown.width());
            assertEquals(64, shown.stride());
        }
    }

    @Test
    @DisplayName("a buffer of the other form is replaced, not reused, though the size is the same")
    void formChangeReplacesTheBuffer() {
        try (var arena = Arena.ofConfined()) {
            var frame = frame(arena, PixelFormat.NV12, 4, 2, 0);
            var frames = new FrameQueue();
            var converted = frames.obtain(FrameQueue.Shape.of(frame, PictureForm.CONVERTED), 0, STOPPED);
            assertNotNull(converted);
            frames.recycle(converted);
            var planes = frames.obtain(FrameQueue.Shape.of(frame, PictureForm.PLANES), 0, STOPPED);
            assertNotNull(planes);
            assertNotSame(converted, planes);
            assertFalse(planes.fits(FrameQueue.Shape.of(frame, PictureForm.CONVERTED)));
            // Of the same size and another layout is another shape too.
            assertFalse(planes.fits(new FrameQueue.Shape(PixelFormat.I420, 4, 2)));
        }
    }

    @Test
    @DisplayName("a picture of another shape than the slot's is refused before a byte is copied")
    void copyRefusesAnotherShape() {
        try (var arena = Arena.ofConfined()) {
            var frames = new FrameQueue();
            var slot = frames.obtain(new FrameQueue.Shape(PixelFormat.NV12, 4, 2), 0, STOPPED);
            assertNotNull(slot);
            assertThrows(
                    IllegalArgumentException.class, () -> slot.copyPlanes(frame(arena, PixelFormat.I420, 4, 2, 0)));
            assertThrows(
                    IllegalArgumentException.class, () -> slot.copyPlanes(frame(arena, PixelFormat.NV12, 6, 2, 0)));
            var converted = frames.obtain(SMALL, 0, STOPPED);
            assertNotNull(converted);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> converted.copyPlanes(frame(arena, PixelFormat.NV12, 4, 2, 0)));
        }
    }

    @Test
    @DisplayName("counts a picture shown once however often it is handed out, and one the clock passed as passed")
    void counts() {
        var frames = new FrameQueue();
        for (var i = 0; i < FrameQueue.CAPACITY; i++) {
            var slot = frames.obtain(SMALL, 0, STOPPED);
            assertNotNull(slot);
            assertTrue(frames.put(slot, i * SECOND, 0));
        }
        // The first is shown, twice; nothing passed.
        frames.present(0, true);
        frames.present(0, true);
        assertEquals(1, frames.shownCount());
        assertEquals(0, frames.passedCount());

        // The clock jumps past the second to the third: the second was never
        // handed out, so it played unseen.
        frames.present(2 * SECOND, true);
        assertEquals(2, frames.shownCount());
        assertEquals(1, frames.passedCount());

        // A flush's pictures are neither: a seek dropped them, not the clock.
        var slot = frames.obtain(SMALL, 0, STOPPED);
        assertNotNull(slot);
        assertTrue(frames.put(slot, 3 * SECOND, 0));
        frames.flush(1);
        var after = frames.obtain(SMALL, 1, STOPPED);
        assertNotNull(after);
        assertTrue(frames.put(after, 10 * SECOND, 1));
        frames.present(0, true);
        assertEquals(3, frames.shownCount());
        assertEquals(1, frames.passedCount(), "the picture a seek replaced was not passed by the clock");
    }

    @Test
    @DisplayName("a shape has a size")
    void shapeHasASize() {
        assertThrows(IllegalArgumentException.class, () -> FrameQueue.Shape.converted(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new FrameQueue.Shape(PixelFormat.NV12, 4, -1));
    }
}
