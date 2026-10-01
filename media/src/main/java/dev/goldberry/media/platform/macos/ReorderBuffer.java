package dev.goldberry.media.platform.macos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.ToLongFunction;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.Frame;

/// Puts decoded pictures back into presentation order.
///
/// VideoToolbox hands pictures over in the order they were decoded, which with
/// B-frames is not the order they are shown in. A stream promises, in its
/// parameter sets, that no picture is shown after more than `depth` pictures that
/// were decoded after it (`ParameterSets.Shape.reorderDepth`). So once more than
/// `depth` pictures are held, the earliest of them can go: nothing still to come
/// is shown before it.
///
/// A picture with no presentation time keeps its place in decoding order. Ties
/// go in decoding order too.
///
/// @param <T> a decoded picture
final class ReorderBuffer<T> {

    private record Entry<T>(T item, long key, long sequence) {}

    private final int depth;
    private final ToLongFunction<T> pts;
    private final PriorityQueue<Entry<T>> held =
            new PriorityQueue<>(Comparator.<Entry<T>>comparingLong(Entry::key).thenComparingLong(Entry::sequence));
    private long sequence;
    private long lastKey = Long.MIN_VALUE;

    /// A buffer that holds `depth` pictures back.
    ///
    /// @param pts a picture's presentation time in nanoseconds, or [Frame#NO_PTS]
    ReorderBuffer(int depth, ToLongFunction<T> pts) {
        if (depth < 0) {
            throw new IllegalArgumentException("a depth of " + depth);
        }
        this.depth = depth;
        this.pts = Objects.requireNonNull(pts, "pts");
    }

    /// Takes a picture in decoding order.
    void add(T item) {
        var time = pts.applyAsLong(item);
        // A picture with no time sorts where the latest one that had a time did,
        // and after it: its decoding position is all that is known.
        var key = time == Frame.NO_PTS ? lastKey : time;
        lastKey = Math.max(lastKey, key);
        held.add(new Entry<>(item, key, sequence++));
    }

    /// The next picture to show, if more than `depth` are held; otherwise null.
    @Nullable
    T poll() {
        return held.size() > depth ? next() : null;
    }

    /// The next picture to show whatever is held, for when no more are coming:
    /// null only when the buffer is empty.
    @Nullable
    T drain() {
        return next();
    }

    /// Empties the buffer, and answers what it held so the caller can free it.
    List<T> clear() {
        var items = new ArrayList<T>(held.size());
        for (var entry : held) {
            items.add(entry.item());
        }
        held.clear();
        lastKey = Long.MIN_VALUE;
        return items;
    }

    /// How many pictures are held.
    int size() {
        return held.size();
    }

    private @Nullable T next() {
        var entry = held.poll();
        return entry == null ? null : entry.item();
    }
}
