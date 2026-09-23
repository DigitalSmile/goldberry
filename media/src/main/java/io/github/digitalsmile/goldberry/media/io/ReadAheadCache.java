package io.github.digitalsmile.goldberry.media.io;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/// The bytes an [HttpIO] has fetched and not yet thrown away: the read-ahead
/// cache of `docs/goldberry-media.md` §4.
///
/// **Extents.** The cache holds disjoint runs of the resource, each a list of
/// [#CHUNK]-sized arrays, so that a run grows without copying and its front can
/// be dropped a chunk at a time. Every chunk but an extent's last is full, which
/// makes finding a byte a division. A run that grows into the next one absorbs it,
/// so two extents never touch: [#ranges()] is exactly what is at hand.
///
/// **Eviction** ([#evict]) keeps the cache under its capacity by giving up what
/// is least likely to be read next: whole extents away from the reader, farthest
/// first, then the part of the reader's own extent it has already read. Never
/// the bytes ahead of the reader in its extent. Those are the read-ahead, and
/// [HttpIO] bounds them to well under the capacity.
///
/// **Not thread-safe.** [HttpIO] calls it with its lock held.
final class ReadAheadCache {

    /// The unit the cache allocates, grows and evicts in.
    static final int CHUNK = 64 * 1024;

    /// A run of consecutive bytes, from [#start] for [#length] bytes.
    private static final class Extent {
        long start;
        long length;
        final ArrayList<byte[]> chunks = new ArrayList<>();

        Extent(long start) {
            this.start = start;
        }

        long end() {
            return start + length;
        }

        /// Appends `length` bytes of `source` from `offset`.
        void append(byte[] source, int offset, int length) {
            var copied = 0;
            while (copied < length) {
                var used = (int) (this.length % CHUNK);
                if (used == 0) {
                    chunks.add(new byte[CHUNK]);
                }
                var count = Math.min(CHUNK - used, length - copied);
                System.arraycopy(source, offset + copied, chunks.getLast(), used, count);
                copied += count;
                this.length += count;
            }
        }

        /// Copies bytes from `position`, which this extent holds, into `target`.
        int read(long position, ByteBuffer target) {
            var copied = 0;
            var at = position;
            while (target.hasRemaining() && at < end()) {
                var offset = at - start;
                var chunk = chunks.get((int) (offset / CHUNK));
                var inChunk = (int) (offset % CHUNK);
                var count = (int) Math.min(Math.min(CHUNK - inChunk, end() - at), target.remaining());
                target.put(chunk, inChunk, count);
                copied += count;
                at += count;
            }
            return copied;
        }

        /// Appends every byte of `other` from `from` on.
        void absorb(Extent other, long from) {
            var at = from;
            while (at < other.end()) {
                var offset = at - other.start;
                var chunk = other.chunks.get((int) (offset / CHUNK));
                var inChunk = (int) (offset % CHUNK);
                var count = (int) Math.min(CHUNK - inChunk, other.end() - at);
                append(chunk, inChunk, count);
                at += count;
            }
        }

        /// Drops the first chunk, if it is full: the front moves on by a chunk.
        boolean dropFirstChunk() {
            if (length < CHUNK) {
                return false;
            }
            chunks.removeFirst();
            start += CHUNK;
            length -= CHUNK;
            return true;
        }
    }

    private final TreeMap<Long, Extent> extents = new TreeMap<>();
    private final long capacity;
    private long size;

    /// A cache that [#evict]s down to `capacity` bytes.
    ReadAheadCache(long capacity) {
        if (capacity < 2L * CHUNK) {
            throw new IllegalArgumentException("capacity " + capacity + " is under two chunks");
        }
        this.capacity = capacity;
    }

    /// Records `length` bytes of `source` from `offset` as the resource's bytes at
    /// `position`. Bytes already held are left as they are.
    void write(long position, byte[] source, int offset, int length) {
        if (length <= 0) {
            return;
        }
        var extent = extentAt(position);
        var at = position;
        var from = offset;
        var remaining = length;
        if (extent != null && at < extent.end()) {
            // Held already, in part: skip what is, and append what is not.
            var skip = (int) Math.min(extent.end() - at, remaining);
            at += skip;
            from += skip;
            remaining -= skip;
            if (remaining == 0) {
                return;
            }
        }
        if (extent == null) {
            extent = new Extent(at);
            extents.put(at, extent);
        }
        // An extent may not grow over the next one: it absorbs it instead.
        var next = extents.higherEntry(extent.start);
        var room = next == null ? remaining : (int) Math.min(remaining, next.getKey() - at);
        extent.append(source, from, room);
        size += room;
        if (next != null && extent.end() == next.getKey()) {
            // Moved, not counted twice: the absorbed bytes leave `size` and come
            // back as the merged extent's.
            var absorbed = next.getValue();
            extents.remove(next.getKey());
            extent.absorb(absorbed, absorbed.start);
            // The rest of `source` overlaps what was absorbed; written again, the
            // held part is skipped and anything past it appended.
            if (room < remaining) {
                write(at + room, source, from + room, remaining - room);
            }
        }
    }

    /// Copies what the cache holds from `position` into `target`, up to the end of
    /// the extent that holds `position` or `target`'s end, whichever comes first.
    ///
    /// @return how many bytes were copied: 0 when `position` is not held
    int read(long position, ByteBuffer target) {
        var extent = extentAt(position);
        return extent == null || position >= extent.end() ? 0 : extent.read(position, target);
    }

    /// The first byte at or after `position` that the cache does not hold: the
    /// end of the extent holding `position`, or `position` itself.
    long firstMissing(long position) {
        var extent = extentAt(position);
        return extent == null || position >= extent.end() ? position : extent.end();
    }

    /// What the cache holds, in order, disjoint and never touching.
    List<ByteRange> ranges() {
        var ranges = new ArrayList<ByteRange>(extents.size());
        for (var extent : extents.values()) {
            if (extent.length > 0) {
                ranges.add(new ByteRange(extent.start, extent.end()));
            }
        }
        return List.copyOf(ranges);
    }

    /// How many bytes the cache holds.
    long size() {
        return size;
    }

    /// Gives up bytes until the cache is within its capacity, keeping those the
    /// reader at `reader` is likeliest to want (see the class comment).
    void evict(long reader) {
        if (size <= capacity) {
            return;
        }
        var own = extentAt(reader);
        var others = new ArrayList<Extent>(extents.values());
        others.remove(own);
        others.sort(Comparator.comparingLong((Extent extent) -> distance(extent, reader))
                .reversed());
        for (var extent : others) {
            if (size <= capacity) {
                return;
            }
            extents.remove(extent.start);
            size -= extent.length;
        }
        if (own == null) {
            return;
        }
        // The part of the reader's extent already read, a chunk at a time.
        while (size > capacity && own.start + CHUNK <= reader) {
            extents.remove(own.start);
            if (!own.dropFirstChunk()) {
                extents.put(own.start, own);
                return;
            }
            size -= CHUNK;
            extents.put(own.start, own);
        }
    }

    /// Forgets everything.
    void clear() {
        extents.clear();
        size = 0;
    }

    /// The extent that holds `position`, or ends exactly at it, or null.
    private @Nullable Extent extentAt(long position) {
        var floor = extents.floorEntry(position);
        if (floor == null) {
            return null;
        }
        var extent = floor.getValue();
        return position <= extent.end() ? extent : null;
    }

    private static long distance(Extent extent, long reader) {
        return reader >= extent.end() ? reader - extent.end() : extent.start - reader;
    }
}
