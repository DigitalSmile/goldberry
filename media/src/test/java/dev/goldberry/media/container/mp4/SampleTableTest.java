package dev.goldberry.media.container.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaException;

/// The sample tables flattened: the run-length boxes of a small made-up track
/// against the rows they stand for.
@DisplayName("SampleTable")
class SampleTableTest {

    /// Six samples in three chunks of 2, 1 and 3; two time runs; a ctts of
    /// B-frame shape; sync samples 1 and 4.
    private static SampleTable six() {
        var builder = new SampleTable.Builder();
        builder.timeRun(4, 10);
        builder.timeRun(2, 5);
        builder.offsetRun(1, 20);
        builder.offsetRun(2, 0);
        builder.offsetRun(3, 10);
        builder.chunkRun(1, 2);
        builder.chunkRun(2, 1);
        builder.chunkRun(3, 3);
        builder.sizes(new int[] {100, 200, 300, 400, 500, 600});
        builder.chunkOffsets(new long[] {1000, 2000, 3000});
        builder.syncSamples(new long[] {1, 4});
        assertTrue(builder.hasSamples());
        return builder.build();
    }

    @Test
    @DisplayName("offsets run through each chunk, sample sizes adding up")
    void offsets() {
        var table = six();
        assertEquals(6, table.count());
        assertEquals(1000, table.offset(0));
        assertEquals(1100, table.offset(1));
        assertEquals(2000, table.offset(2));
        assertEquals(3000, table.offset(3));
        assertEquals(3400, table.offset(4));
        assertEquals(3900, table.offset(5));
        assertEquals(600, table.size(5));
    }

    @Test
    @DisplayName("decode times are the stts deltas summed, presentation times add the ctts offsets")
    void times() {
        var table = six();
        assertEquals(0, table.decodeTime(0));
        assertEquals(10, table.decodeTime(1));
        assertEquals(40, table.decodeTime(4));
        assertEquals(45, table.decodeTime(5));
        assertEquals(10, table.duration(0));
        assertEquals(5, table.duration(5));
        assertEquals(50, table.totalDuration());
        assertEquals(20, table.presentationTime(0));
        assertEquals(10, table.presentationTime(1));
        assertEquals(40, table.presentationTime(3));
    }

    @Test
    @DisplayName("stss names the sync samples from 1; without it every sample is one")
    void sync() {
        var table = six();
        assertTrue(table.isSync(0));
        assertFalse(table.isSync(1));
        assertTrue(table.isSync(3));
        var builder = new SampleTable.Builder();
        builder.timeRun(2, 1);
        builder.chunkRun(1, 2);
        builder.sizes(new int[] {1, 1});
        builder.chunkOffsets(new long[] {0});
        var all = builder.build();
        assertTrue(all.isSync(0) && all.isSync(1));
    }

    @Test
    @DisplayName("a seek finds the last sync sample at or before a time, else the first, and any sample for the rest")
    void seeks() {
        var table = six();
        assertEquals(3, table.lastSyncAtOrBefore(45));
        assertEquals(3, table.lastSyncAtOrBefore(40));
        assertEquals(0, table.lastSyncAtOrBefore(39));
        assertEquals(0, table.lastSyncAtOrBefore(20));
        assertEquals(0, table.lastSyncAtOrBefore(0), "before the first: the first");
        assertEquals(5, table.lastAtOrBefore(1000));
        assertEquals(1, table.lastAtOrBefore(19));
        assertEquals(0, table.lastAtOrBefore(0));
    }

    @Test
    @DisplayName("an stts that stops short keeps its last delta; a table with no sizes or chunks is empty")
    void shortTables() {
        var builder = new SampleTable.Builder();
        builder.timeRun(1, 7);
        builder.chunkRun(1, 3);
        builder.sizes(new int[] {1, 2, 3});
        builder.chunkOffsets(new long[] {10});
        var table = builder.build();
        assertEquals(14, table.decodeTime(2));
        assertEquals(7, table.duration(2));
        assertEquals(13, table.offset(2));

        assertSame(SampleTable.EMPTY, new SampleTable.Builder().build());
        var noChunks = new SampleTable.Builder();
        noChunks.sizes(new int[] {1});
        assertSame(SampleTable.EMPTY, noChunks.build());
    }

    @Test
    @DisplayName("chunk tables that place too few samples are invalid data")
    void tooFewChunks() {
        var builder = new SampleTable.Builder();
        builder.timeRun(3, 1);
        builder.chunkRun(1, 1);
        builder.sizes(new int[] {1, 2, 3});
        builder.chunkOffsets(new long[] {10, 20});
        assertThrows(MediaException.class, builder::build);
    }

    @Test
    @DisplayName("appending a fragment keeps both halves' rows and sync flags")
    void appends() {
        var sync = new BitSet();
        sync.set(1);
        var fragment = SampleTable.ofRows(
                new long[] {5000, 5100},
                new int[] {100, 50},
                new long[] {50, 60},
                new int[] {0, 0},
                new int[] {10, 10},
                sync);
        var table = six().append(fragment);
        assertEquals(8, table.count());
        assertEquals(5100, table.offset(7));
        assertEquals(60, table.decodeTime(7));
        assertFalse(table.isSync(6));
        assertTrue(table.isSync(7));
        assertEquals(70, table.totalDuration());
        assertSame(fragment, SampleTable.EMPTY.append(fragment));
        assertSame(fragment, fragment.append(SampleTable.EMPTY));
    }
}
