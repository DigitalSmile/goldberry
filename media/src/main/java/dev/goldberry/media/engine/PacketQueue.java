package dev.goldberry.media.engine;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;

/// The queue between the demux thread and one track's decode thread
/// (`docs/goldberry-media.md` §3, "Packet queue").
///
/// Bounded by **duration**, not by count: 64 packets are three seconds of Opus
/// and a quarter of a second of 4K video. And by **bytes** as well, because a
/// container may not say how long its packets are (WebM often does not), and a
/// packet with no duration would otherwise be free.
///
/// **[#put] never blocks.** With one queue per track, a demux thread that blocked
/// on the full one could starve the other: video's queue full, audio's empty, the
/// audio clock stopped for want of samples, and video waiting for that clock. So
/// the demux thread asks every queue whether it is [#full()] and waits only when
/// all of them are, the rule ffplay's reader follows. A queue can therefore run
/// past its bound while another fills. [#overflowing()] is the hard limit past
/// which the demux thread waits anyway.
///
/// **Serial.** Every item carries the Serial it was queued under. A seek bumps
/// the Serial and [#flush]es: the packets already queued are closed and dropped,
/// and a [Item.Flush] marker goes in their place, so the decode thread flushes its
/// decoder exactly once, at exactly the point where the new position's packets
/// begin. A packet with a stale Serial that slipped past (read before the seek,
/// queued after it) is dropped by the decode thread when it sees it.
///
/// **How far it reaches.** [#endNanos()] is the end of the latest packet queued
/// since the last flush: how far into the stream the demux thread has read for
/// this track. Less the clock, it is what the track can play without another
/// byte, which is what the water marks are measured against.
///
/// [#abort()] wakes the consumer for good. It is how the Engine stops.
final class PacketQueue {

    /// How far past its bounds a queue may grow while another track's queue fills,
    /// before the demux thread waits regardless.
    static final int OVERFLOW_FACTOR = 4;

    /// What the decode thread takes.
    sealed interface Item {
        /// The Serial this item belongs to.
        int serial();

        /// A packet to decode.
        record Data(Packet packet, int serial) implements Item {}

        /// The source has no more packets at this Serial: drain the decoder.
        record End(int serial) implements Item {}

        /// A seek happened: flush the decoder, and discard decoded media before
        /// `targetNanos` when `accurate`.
        record Flush(int serial, long targetNanos, boolean accurate) implements Item {}
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final ArrayDeque<Item> items = new ArrayDeque<>();
    private final long capacityNanos;
    private final long capacityBytes;
    private long queuedNanos;
    private long queuedBytes;
    private long endNanos = Frame.NO_PTS;
    private boolean ended;
    private boolean aborted;

    /// A queue that holds up to `capacityNanos` of media, or `capacityBytes` of
    /// packets, whichever comes first.
    PacketQueue(long capacityNanos, long capacityBytes) {
        if (capacityNanos <= 0 || capacityBytes <= 0) {
            throw new IllegalArgumentException("capacity " + capacityNanos + " ns, " + capacityBytes + " bytes");
        }
        this.capacityNanos = capacityNanos;
        this.capacityBytes = capacityBytes;
    }

    /// The most media the queue holds before it says it is full.
    long capacityNanos() {
        return capacityNanos;
    }

    /// Queues a packet, at once. A packet whose duration is unknown counts only
    /// its bytes.
    ///
    /// @return false when the queue was aborted; the packet is closed then,
    ///         because nobody will take it
    boolean put(Packet packet, int serial) {
        lock.lock();
        try {
            if (aborted) {
                packet.close();
                return false;
            }
            var data = new Item.Data(packet, serial);
            items.addLast(data);
            queuedNanos += durationOf(data);
            queuedBytes += packet.data().byteSize();
            endNanos = Math.max(endNanos, endOf(packet));
            ended = false;
            notEmpty.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /// Whether the queue holds as much as it is meant to: the demux thread may
    /// stop reading for it.
    boolean full() {
        lock.lock();
        try {
            return queuedNanos >= capacityNanos || queuedBytes >= capacityBytes;
        } finally {
            lock.unlock();
        }
    }

    /// Whether the queue has run so far past its bounds that the demux thread
    /// must wait however hungry another queue is.
    boolean overflowing() {
        lock.lock();
        try {
            return queuedNanos >= capacityNanos * OVERFLOW_FACTOR || queuedBytes >= capacityBytes * OVERFLOW_FACTOR;
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
    void flush(int serial, long targetNanos, boolean accurate) {
        lock.lock();
        try {
            closeQueued();
            ended = false;
            endNanos = Frame.NO_PTS;
            items.addLast(new Item.Flush(serial, targetNanos, accurate));
            notEmpty.signalAll();
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
                queuedBytes = Math.max(0, queuedBytes - data.packet().data().byteSize());
            }
            return item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    /// The next item, if it is a [Item.Flush], taken; anything else stays
    /// queued. What a paused decode thread uses to honour a seek without playing
    /// on.
    Item.@Nullable Flush takeFlush() {
        lock.lock();
        try {
            if (!aborted && items.peekFirst() instanceof Item.Flush flush) {
                items.pollFirst();
                return flush;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /// Whether a packet is waiting, as opposed to nothing or only markers.
    boolean hasPackets() {
        lock.lock();
        try {
            for (var item : items) {
                if (item instanceof Item.Data) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /// Whether the next item is a [Item.Flush]: a seek is waiting to be honoured.
    boolean flushQueued() {
        lock.lock();
        try {
            return !aborted && items.peekFirst() instanceof Item.Flush;
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

    /// How many bytes of packets are queued.
    long queuedBytes() {
        lock.lock();
        try {
            return queuedBytes;
        } finally {
            lock.unlock();
        }
    }

    /// The stream time just past the latest packet queued since the last flush,
    /// or [Frame#NO_PTS] before one. A packet with no timestamp does not move it.
    long endNanos() {
        lock.lock();
        try {
            return endNanos;
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

    /// Wakes the consumer for good, and closes every queued packet.
    void abort() {
        lock.lock();
        try {
            aborted = true;
            closeQueued();
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Called with the lock held.
    private void closeQueued() {
        for (var item : items) {
            if (item instanceof Item.Data(var packet, var _)) {
                packet.close();
            }
        }
        items.clear();
        queuedNanos = 0;
        queuedBytes = 0;
    }

    /// Where `packet` ends: its presentation time, or its decoding time when it
    /// has none, plus its duration.
    private static long endOf(Packet packet) {
        var time = packet.pts() != Packet.NO_TIMESTAMP ? packet.pts() : packet.dts();
        if (time == Packet.NO_TIMESTAMP) {
            return Frame.NO_PTS;
        }
        return packet.timeBase().toNanos(time + Math.max(packet.duration(), 0));
    }

    private static long durationOf(Item.Data data) {
        var packet = data.packet();
        return packet.duration() > 0 ? packet.timeBase().toNanos(packet.duration()) : 0;
    }
}
