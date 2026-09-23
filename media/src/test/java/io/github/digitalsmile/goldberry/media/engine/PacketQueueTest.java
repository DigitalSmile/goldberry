package io.github.digitalsmile.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.Rational;

/// The queue's rules, with no FFmpeg: duration-bounded, Serial-aware, and
/// wakeable from both ends.
@DisplayName("PacketQueue")
class PacketQueueTest {

    private static final Rational MS = new Rational(1, 1000);

    private final AtomicInteger closed = new AtomicInteger();

    /// A packet that plays `millis` and counts its closing.
    private Packet packet(long millis) {
        return Packet.owning(MemorySegment.NULL, 0, 0, 0, millis, true, MS, closed::incrementAndGet);
    }

    @Test
    @DisplayName("hands items over in order, with their Serial")
    void order() {
        var queue = new PacketQueue(1_000_000_000L);
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
        var queue = new PacketQueue(1_000_000_000L);
        queue.put(packet(30), 0);
        queue.put(packet(20), 0);
        assertEquals(50_000_000L, queue.queuedNanos());
        queue.take(1, TimeUnit.SECONDS);
        assertEquals(20_000_000L, queue.queuedNanos());
        queue.take(1, TimeUnit.SECONDS);
        assertNull(queue.take(10, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("a flush closes what is queued and leaves one marker with the new Serial")
    void flush() {
        var queue = new PacketQueue(1_000_000_000L);
        queue.put(packet(10), 0);
        queue.put(packet(10), 0);
        queue.flush(1, 5_000_000_000L);
        assertEquals(2, closed.get());
        assertEquals(0, queue.queuedNanos());
        var flush = assertInstanceOf(PacketQueue.Item.Flush.class, queue.take(1, TimeUnit.SECONDS));
        assertEquals(1, flush.serial());
        assertEquals(5_000_000_000L, flush.targetNanos());
    }

    @Test
    @DisplayName("a full queue blocks the producer until a take makes room")
    void blocksWhenFull() throws Exception {
        var queue = new PacketQueue(20_000_000L);
        queue.put(packet(20), 0);
        var second = CompletableFuture.supplyAsync(() -> queue.put(packet(20), 0));
        Thread.sleep(50);
        assertFalse(second.isDone());
        queue.take(1, TimeUnit.SECONDS);
        assertTrue(second.get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("a wake ends a waiting put, which closes its packet; a later put is not affected")
    void wakeProducer() throws Exception {
        var queue = new PacketQueue(20_000_000L);
        queue.put(packet(20), 0);
        var waiting = CompletableFuture.supplyAsync(() -> queue.put(packet(20), 0));
        Thread.sleep(50);
        queue.wakeProducer();
        assertFalse(waiting.get(1, TimeUnit.SECONDS));
        assertEquals(1, closed.get());

        queue.take(1, TimeUnit.SECONDS);
        assertTrue(queue.put(packet(10), 0), "a wake is not a flag left for the next put");
    }

    @Test
    @DisplayName("abort wakes both sides for good and closes what is queued")
    void abort() throws Exception {
        var queue = new PacketQueue(20_000_000L);
        queue.put(packet(20), 0);
        var producer = CompletableFuture.supplyAsync(() -> queue.put(packet(20), 0));
        Thread.sleep(50);
        queue.abort();
        assertFalse(producer.get(1, TimeUnit.SECONDS));
        assertEquals(2, closed.get());
        assertNull(queue.take(1, TimeUnit.SECONDS));
        assertFalse(queue.put(packet(1), 0));
    }
}
