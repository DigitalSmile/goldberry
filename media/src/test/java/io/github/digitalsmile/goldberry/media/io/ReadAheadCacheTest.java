package io.github.digitalsmile.goldberry.media.io;

import static io.github.digitalsmile.goldberry.media.io.ReadAheadCache.CHUNK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The read-ahead cache on its own: extents, merging, reads that cross chunks,
/// and what eviction gives up first.
@DisplayName("ReadAheadCache")
class ReadAheadCacheTest {

    private static final byte[] DATA = HttpIOTest.pattern(10 * CHUNK);

    private static void put(ReadAheadCache cache, long from, long to) {
        cache.write(from, DATA, (int) from, (int) (to - from));
    }

    private static byte[] read(ReadAheadCache cache, long position, int count) {
        var buffer = ByteBuffer.allocate(count);
        var read = cache.read(position, buffer);
        return Arrays.copyOf(buffer.array(), read);
    }

    @Test
    @DisplayName("holds nothing until written, and says so")
    void empty() {
        var cache = new ReadAheadCache(4L * CHUNK);
        assertEquals(0, cache.read(0, ByteBuffer.allocate(10)));
        assertEquals(123, cache.firstMissing(123));
        assertEquals(List.of(), cache.ranges());
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("reads back what was written, across chunk boundaries")
    void acrossChunks() {
        var cache = new ReadAheadCache(8L * CHUNK);
        // In odd pieces, so appends end inside chunks.
        for (var at = 0; at < 3 * CHUNK; at += 10_007) {
            put(cache, at, Math.min(at + 10_007, 3 * CHUNK));
        }
        assertEquals(List.of(new ByteRange(0, 3L * CHUNK)), cache.ranges());
        assertArrayEquals(Arrays.copyOfRange(DATA, CHUNK - 5, 2 * CHUNK + 5), read(cache, CHUNK - 5, CHUNK + 10));
        assertEquals(3L * CHUNK, cache.firstMissing(17));
        assertEquals(3L * CHUNK, cache.size());
        // A read stops at the end of what is held.
        assertEquals(100, read(cache, 3L * CHUNK - 100, 1_000).length);
    }

    @Test
    @DisplayName("keeps separate runs apart, and joins them when one grows into the next")
    void mergesExtents() {
        var cache = new ReadAheadCache(8L * CHUNK);
        put(cache, 0, 1_000);
        put(cache, 5_000, 9_000);
        assertEquals(List.of(new ByteRange(0, 1_000), new ByteRange(5_000, 9_000)), cache.ranges());
        assertEquals(1_000, cache.firstMissing(500));
        assertEquals(0, cache.read(2_000, ByteBuffer.allocate(10)));

        put(cache, 1_000, 5_000);
        assertEquals(List.of(new ByteRange(0, 9_000)), cache.ranges());
        assertEquals(9_000, cache.size());
        assertArrayEquals(Arrays.copyOfRange(DATA, 0, 9_000), read(cache, 0, 9_000));
    }

    @Test
    @DisplayName("a write over held bytes keeps them, and appends what runs past")
    void overlappingWrite() {
        var cache = new ReadAheadCache(8L * CHUNK);
        put(cache, 0, 1_000);
        put(cache, 2_000, 3_000);
        // From inside the first run, through the gap and the second, and beyond.
        put(cache, 500, 4_000);
        assertEquals(List.of(new ByteRange(0, 4_000)), cache.ranges());
        assertEquals(4_000, cache.size());
        assertArrayEquals(Arrays.copyOfRange(DATA, 0, 4_000), read(cache, 0, 4_000));
    }

    @Test
    @DisplayName("evicts other runs first, farthest from the reader first")
    void evictsFarthest() {
        var cache = new ReadAheadCache(3L * CHUNK);
        put(cache, 0, CHUNK);
        put(cache, 4L * CHUNK, 5L * CHUNK);
        put(cache, 9L * CHUNK, 10L * CHUNK);
        put(cache, 2L * CHUNK, 3L * CHUNK);
        assertEquals(4L * CHUNK, cache.size());
        // The reader is in the run at 2: the run at 9 is the farthest.
        cache.evict(2L * CHUNK + 10);
        assertEquals(
                List.of(
                        new ByteRange(0, CHUNK),
                        new ByteRange(2L * CHUNK, 3L * CHUNK),
                        new ByteRange(4L * CHUNK, 5L * CHUNK)),
                cache.ranges());
        assertEquals(3L * CHUNK, cache.size());
    }

    @Test
    @DisplayName("then gives up what the reader has read of its own run, a chunk at a time, and never what is ahead")
    void trimsBehindTheReader() {
        var cache = new ReadAheadCache(2L * CHUNK);
        put(cache, 0, 5L * CHUNK);
        cache.evict(3L * CHUNK + 7);
        // Down to the capacity: the three chunks wholly behind the reader went.
        assertEquals(List.of(new ByteRange(3L * CHUNK, 5L * CHUNK)), cache.ranges());
        assertEquals(2L * CHUNK, cache.size());
        assertArrayEquals(Arrays.copyOfRange(DATA, 3 * CHUNK + 7, 4 * CHUNK), read(cache, 3L * CHUNK + 7, CHUNK - 7));

        // With the reader at the front, nothing ahead of it may go, even over
        // the capacity.
        var ahead = new ReadAheadCache(2L * CHUNK);
        put(ahead, 0, 3L * CHUNK);
        ahead.evict(10);
        assertEquals(3L * CHUNK, ahead.size());
    }

    @Test
    @DisplayName("keeps a run the reader has not reached yet when it is the nearest")
    void keepsWhatIsNext() {
        var cache = new ReadAheadCache(2L * CHUNK);
        put(cache, 5L * CHUNK, 6L * CHUNK);
        put(cache, 0, 2L * CHUNK);
        // The reader sits in the gap, just before the run at 5.
        cache.evict(5L * CHUNK - 1);
        assertEquals(List.of(new ByteRange(5L * CHUNK, 6L * CHUNK)), cache.ranges());
    }

    @Test
    @DisplayName("forgets everything when cleared")
    void clears() {
        var cache = new ReadAheadCache(2L * CHUNK);
        put(cache, 0, 100);
        cache.clear();
        assertEquals(List.of(), cache.ranges());
        assertEquals(0, cache.size());
        assertFalse(cache.read(0, ByteBuffer.allocate(1)) > 0);
    }

    @Test
    @DisplayName("needs room for two chunks at least")
    void capacity() {
        assertThrows(IllegalArgumentException.class, () -> new ReadAheadCache(CHUNK));
        assertTrue(new ReadAheadCache(2L * CHUNK).ranges().isEmpty());
    }

    @Test
    @DisplayName("ByteRange is half-open and refuses what is not a range")
    void byteRange() {
        var range = new ByteRange(10, 20);
        assertEquals(10, range.length());
        assertTrue(range.contains(10));
        assertFalse(range.contains(20));
        assertThrows(IllegalArgumentException.class, () -> new ByteRange(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new ByteRange(5, 4));
    }
}
