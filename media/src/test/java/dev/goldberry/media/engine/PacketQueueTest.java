package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;

/// The queue's rules, with no FFmpeg: bounded by duration and by bytes,
/// Serial-aware, never blocking its producer, and abortable.
@DisplayName("PacketQueue")
class PacketQueueTest {

    private static final Rational MS = new Rational(1, 1000);
    private static final long SECOND = 1_000_000_000L;

    private final AtomicInteger closed = new AtomicInteger();
    private final Arena arena = Arena.ofConfined();

    @AfterEach
    void free() {
        arena.close();
    }

    /// A packet that plays `millis` and counts its closing.
    private Packet packet(long millis) {
        return Packet.owning(MemorySegment.NULL, 0, 0, 0, millis, true, MS, closed::incrementAndGet);
    }

    /// A packet of `bytes` with no duration, as WebM often hands them over.
    private Packet bytes(long bytes) {
        return Packet.owning(arena.allocate(bytes), 0, 0, 0, 0, true, MS, closed::incrementAndGet);
    }

    @Test
    @DisplayName("hands items over in order, with their Serial")
    void order() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        var first = packet(10);
        assertTrue(queue.put(first, 0));
        queue.end(0);
        var data = assertInstanceOf(PacketQueue.Item.Data.class, queue.take(1, TimeUnit.SECONDS));
        assertEquals(first, data.packet());
        assertInstanceOf(PacketQueue.Item.End.class, queue.take(1, TimeUnit.SECONDS));
        assertTrue(queue.ended());
    }

    @Test
    @DisplayName("counts queued media by duration, and times out empty")
    void duration() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.put(packet(30), 0);
        queue.put(packet(20), 0);
        assertEquals(50_000_000L, queue.queuedNanos());
        queue.take(1, TimeUnit.SECONDS);
        assertEquals(20_000_000L, queue.queuedNanos());
        queue.take(1, TimeUnit.SECONDS);
        assertNull(queue.take(10, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("a flush closes what is queued and leaves one marker with the new Serial and the seek's mode")
    void flush() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.put(packet(10), 0);
        queue.put(bytes(100), 0);
        queue.flush(1, 5 * SECOND, false);
        assertEquals(2, closed.get());
        assertEquals(0, queue.queuedNanos());
        assertEquals(0, queue.queuedBytes());
        assertTrue(queue.flushQueued());
        var flush = assertInstanceOf(PacketQueue.Item.Flush.class, queue.take(1, TimeUnit.SECONDS));
        assertEquals(1, flush.serial());
        assertEquals(5 * SECOND, flush.targetNanos());
        assertFalse(flush.accurate());
        assertFalse(queue.flushQueued());
    }

    @Test
    @DisplayName("is full by duration, or by bytes when packets have none, and never blocks a put")
    void fullNeverBlocks() {
        var byDuration = new PacketQueue(20_000_000L, 1 << 20);
        byDuration.put(packet(10), 0);
        assertFalse(byDuration.full());
        byDuration.put(packet(10), 0);
        assertTrue(byDuration.full());
        // Past full, at once: the demux thread decides whether to wait, not the queue.
        assertTrue(byDuration.put(packet(10), 0));
        assertFalse(byDuration.overflowing());

        var byBytes = new PacketQueue(SECOND, 1000);
        byBytes.put(bytes(600), 0);
        assertFalse(byBytes.full());
        byBytes.put(bytes(600), 0);
        assertTrue(byBytes.full());
        assertEquals(1200, byBytes.queuedBytes());
        byBytes.take(1, TimeUnit.SECONDS);
        assertEquals(600, byBytes.queuedBytes());
    }

    @Test
    @DisplayName("overflows at four times its bounds")
    void overflows() {
        var queue = new PacketQueue(10_000_000L, 1 << 20);
        for (var i = 0; i < PacketQueue.OVERFLOW_FACTOR - 1; i++) {
            queue.put(packet(10), 0);
        }
        assertFalse(queue.overflowing());
        queue.put(packet(10), 0);
        assertTrue(queue.overflowing());
    }

    @Test
    @DisplayName("takeFlush takes a flush at the head and nothing else")
    void takeFlush() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.put(packet(10), 0);
        assertNull(queue.takeFlush());
        queue.flush(3, SECOND, true);
        var flush = queue.takeFlush();
        assertEquals(3, flush.serial());
        assertTrue(flush.accurate());
        assertNull(queue.takeFlush());
    }

    @Test
    @DisplayName("says how far it reaches: the end of the latest packet, until a flush")
    void reach() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        assertEquals(Frame.NO_PTS, queue.endNanos());
        queue.put(Packet.of(MemorySegment.NULL, 0, 100, 100, 20, true, MS), 0);
        assertEquals(120_000_000L, queue.endNanos());
        // Taking does not move it: the packet is still ahead of the clock.
        queue.take(1, TimeUnit.SECONDS);
        assertEquals(120_000_000L, queue.endNanos());
        // No pts: its dts stands in. No timestamp at all: it does not move.
        queue.put(Packet.of(MemorySegment.NULL, 0, Packet.NO_TIMESTAMP, 200, 0, true, MS), 0);
        assertEquals(200_000_000L, queue.endNanos());
        queue.put(Packet.of(MemorySegment.NULL, 0, Packet.NO_TIMESTAMP, Packet.NO_TIMESTAMP, 50, true, MS), 0);
        assertEquals(200_000_000L, queue.endNanos());
        // A packet out of order (B-frames) does not pull it back.
        queue.put(Packet.of(MemorySegment.NULL, 0, 150, 150, 10, true, MS), 0);
        assertEquals(200_000_000L, queue.endNanos());
        queue.flush(1, 0, true);
        assertEquals(Frame.NO_PTS, queue.endNanos());
    }

    /// A packet at `ptsMillis` that plays `millis`.
    private static Packet at(long ptsMillis, long millis) {
        return Packet.of(MemorySegment.NULL, 0, ptsMillis, ptsMillis, millis, true, MS);
    }

    @Test
    @DisplayName(
            "takeBefore takes the packets at the head that end by the time given, and stops at the first that does not")
    void takeBefore() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.put(at(0, 20), 0);
        queue.put(at(20, 20), 0);
        queue.put(at(40, 20), 0);
        assertEquals(60_000_000L, queue.queuedNanos());

        assertEquals(0, queue.takeBefore(40_000_000L).packet().pts());
        assertEquals(20, queue.takeBefore(40_000_000L).packet().pts());
        assertNull(queue.takeBefore(40_000_000L), "the packet at 40 ms ends at 60 ms, past the time");
        assertEquals(20_000_000L, queue.queuedNanos(), "what is taken leaves the count");
        assertEquals(40, queue.takeBefore(60_000_000L).packet().pts());
    }

    @Test
    @DisplayName("takeBefore takes no marker, and no packet with no timestamp")
    void takeBeforeLeavesTheRest() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.flush(1, SECOND, true);
        assertNull(queue.takeBefore(SECOND), "a flush is the paused thread's to take first");
        assertInstanceOf(PacketQueue.Item.Flush.class, queue.takeFlush());
        queue.put(Packet.of(MemorySegment.NULL, 0, Packet.NO_TIMESTAMP, Packet.NO_TIMESTAMP, 20, true, MS), 1);
        assertNull(queue.takeBefore(SECOND));
        queue.take(1, TimeUnit.SECONDS);
        queue.seam(1, SECOND);
        queue.put(at(0, 20), 1);
        assertNull(queue.takeBefore(SECOND), "a seam stays at the head");
    }

    @Test
    @DisplayName("a seam is a marker with its offset, which moves how far the queue reaches, until a flush")
    void seam() {
        var queue = new PacketQueue(SECOND, 1 << 20);
        queue.put(at(980, 20), 0);
        assertEquals(SECOND, queue.endNanos());
        queue.seam(0, SECOND);
        queue.put(at(0, 20), 0);
        assertEquals(SECOND + 20_000_000L, queue.endNanos(), "the next pass reaches on from the last");

        queue.take(1, TimeUnit.SECONDS);
        var seam = assertInstanceOf(PacketQueue.Item.Seam.class, queue.take(1, TimeUnit.SECONDS));
        assertEquals(new PacketQueue.Item.Seam(0, SECOND), seam);
        assertInstanceOf(PacketQueue.Item.Data.class, queue.take(1, TimeUnit.SECONDS));

        queue.flush(1, 500_000_000L, true);
        queue.put(at(500, 20), 1);
        assertEquals(520_000_000L, queue.endNanos(), "a seek is back in the source's own time");
    }

    @Test
    @DisplayName("abort wakes a waiting take for good and closes what is queued")
    void abort() throws Exception {
        var queue = new PacketQueue(20_000_000L, 1 << 20);
        queue.put(packet(20), 0);
        queue.take(1, TimeUnit.SECONDS);
        var consumer = CompletableFuture.supplyAsync(() -> queue.take(10, TimeUnit.SECONDS));
        Thread.sleep(50);
        queue.put(packet(20), 0);
        consumer.get(1, TimeUnit.SECONDS);
        queue.put(packet(20), 0);
        queue.abort();
        assertEquals(1, closed.get());
        assertNull(queue.take(1, TimeUnit.SECONDS));
        assertNull(queue.takeFlush());
        assertFalse(queue.put(packet(1), 0));
        assertEquals(2, closed.get(), "a packet put after the abort is closed at once");
    }
}
