package io.github.digitalsmile.goldberry.media.engine;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.codec.Packet;

/// The bounded queue between the demux thread and one track's decode thread
/// (`docs/goldberry-media.md` §3, "Packet queue").
///
/// Bounded by **duration**, not by count: 64 packets are three seconds of Opus
/// and a quarter of a second of 4K video. The demux thread blocks when the
/// queue holds [#capacityNanos()] of media, and the decode thread blocks when
/// it is empty.
///
/// **Serial.** Every item carries the Serial it was queued under. A seek bumps
/// the Serial and [#flush]es: the packets already queued are closed and dropped,
/// and a [Item.Flush] marker goes in their place, so the decode thread flushes its
/// decoder exactly once, at exactly the point where the new position's packets
/// begin. A packet with a stale Serial that slipped past (read before the seek,
/// queued after it) is dropped by the decode thread when it sees it.
///
/// [#abort()] wakes both sides for good. It is how the Engine stops.
final class PacketQueue {

    /// What the decode thread takes.
    sealed interface Item {
        /// The Serial this item belongs to.
        int serial();

        /// A packet to decode.
        record Data(Packet packet, int serial) implements Item {}

        /// The source has no more packets at this Serial: drain the decoder.
        record End(int serial) implements Item {}

        /// A seek happened: flush the decoder, and discard decoded media before
        /// `targetNanos`.
        record Flush(int serial, long targetNanos) implements Item {}
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final ArrayDeque<Item> items = new ArrayDeque<>();
    private final long capacityNanos;
    private long queuedNanos;
    private boolean ended;
    private boolean aborted;
    /// Bumped by [#wakeProducer()]. A put compares it with the value it started
    /// with, so a wake reaches only the puts already waiting, never a later one.
    private long wakeGeneration;

    /// A queue that holds up to `capacityNanos` of media.
    PacketQueue(long capacityNanos) {
        if (capacityNanos <= 0) {
            throw new IllegalArgumentException("capacity " + capacityNanos);
        }
        this.capacityNanos = capacityNanos;
    }

    /// The most media the queue holds before [#put] blocks.
    long capacityNanos() {
        return capacityNanos;
    }

    /// Queues a packet, blocking while the queue is full. A packet whose duration
    /// is unknown counts as free.
    ///
    /// @return false when the queue was aborted, or [#wakeProducer()] was called
    ///         while it waited; the packet is closed either way, because it belongs
    ///         to a position the Engine is leaving
    boolean put(Packet packet, int serial) {
        lock.lock();
        try {
            var ticket = wakeGeneration;
            while (!aborted && !wokenSince(ticket) && queuedNanos >= capacityNanos) {
                notFull.awaitUninterruptibly();
            }
            if (aborted || wokenSince(ticket)) {
                packet.close();
                return false;
            }
            var data = new Item.Data(packet, serial);
            items.addLast(data);
            queuedNanos += durationOf(data);
            ended = false;
            notEmpty.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /// Whether [#wakeProducer()] has been called since `ticket` was taken. Called
    /// with the lock held.
    private boolean wokenSince(long ticket) {
        return wakeGeneration != ticket;
    }

    /// Ends every [#put] that is waiting for room: a seek has arrived, and the
    /// demux thread must go and run it rather than wait for the decode thread to
    /// drain a queue that is about to be flushed.
    void wakeProducer() {
        lock.lock();
        try {
            wakeGeneration++;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Queues the end of the source at `serial`.
    void end(int serial) {
        lock.lock();
        try {
            items.addLast(new Item.End(serial));
            ended = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Drops and closes every queued packet, and queues a [Item.Flush] in their
    /// place.
    void flush(int serial, long targetNanos) {
        lock.lock();
        try {
            for (var item : items) {
                if (item instanceof Item.Data(var packet, var ignored)) {
                    packet.close();
                }
            }
            items.clear();
            queuedNanos = 0;
            ended = false;
            items.addLast(new Item.Flush(serial, targetNanos));
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// The next item, blocking up to `timeout` for one.
    ///
    /// @return the item, or null on a timeout or after [#abort()]
    @Nullable
    Item take(long timeout, TimeUnit unit) {
        lock.lock();
        try {
            var remaining = unit.toNanos(timeout);
            while (!aborted && items.isEmpty()) {
                if (remaining <= 0) {
                    return null;
                }
                remaining = notEmpty.awaitNanos(remaining);
            }
            if (aborted) {
                return null;
            }
            var item = Objects.requireNonNull(items.pollFirst());
            if (item instanceof Item.Data data) {
                queuedNanos = Math.max(0, queuedNanos - durationOf(data));
                notFull.signalAll();
            }
            return item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    /// How much media is queued.
    long queuedNanos() {
        lock.lock();
        try {
            return queuedNanos;
        } finally {
            lock.unlock();
        }
    }

    /// Whether the end of the source has been queued and nothing has been queued
    /// after it: the queue will not refill without a seek.
    boolean ended() {
        lock.lock();
        try {
            return ended;
        } finally {
            lock.unlock();
        }
    }

    /// Wakes both sides for good, and closes every queued packet.
    void abort() {
        lock.lock();
        try {
            aborted = true;
            for (var item : items) {
                if (item instanceof Item.Data(var packet, var ignored)) {
                    packet.close();
                }
            }
            items.clear();
            queuedNanos = 0;
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private static long durationOf(Item.Data data) {
        var packet = data.packet();
        return packet.duration() > 0 ? packet.timeBase().toNanos(packet.duration()) : 0;
    }
}
