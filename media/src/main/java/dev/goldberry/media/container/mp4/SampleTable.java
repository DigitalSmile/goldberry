package dev.goldberry.media.container.mp4;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;

/// One track's samples, flattened from the `stbl` boxes into a row per sample:
/// where it is in the file, how long it is, when it decodes, how far its
/// presentation is from that, how long it plays, and whether it is a sync
/// sample.
///
/// The boxes are run-length coded against each other — `stts` runs of equal
/// deltas, `stsc` runs of equal chunk shapes, `stco` per chunk, `stsz` per
/// sample — and every read and every seek wants them per sample. Flattening
/// once at open costs a few dozen bytes a sample, which a feature film's video
/// track turns into tens of megabytes at most, and is what FFmpeg's reader does
/// too.
final class SampleTable {

    /// No samples at all: a track whose `stbl` is empty, as a fragmented file's
    /// is before its fragments are read.
    static final SampleTable EMPTY =
            new SampleTable(new long[0], new int[0], new long[0], new int[0], new int[0], new BitSet());

    private final long[] offsets;
    private final int[] sizes;
    private final long[] decodeTimes;
    private final int[] compositionOffsets;
    private final int[] durations;
    private final BitSet sync;

    private SampleTable(
            long[] offsets, int[] sizes, long[] decodeTimes, int[] compositionOffsets, int[] durations, BitSet sync) {
        this.offsets = offsets;
        this.sizes = sizes;
        this.decodeTimes = decodeTimes;
        this.compositionOffsets = compositionOffsets;
        this.durations = durations;
        this.sync = sync;
    }

    /// How many samples there are.
    int count() {
        return sizes.length;
    }

    /// The first byte of sample `index` in the file.
    long offset(int index) {
        return offsets[index];
    }

    /// The length of sample `index`.
    int size(int index) {
        return sizes[index];
    }

    /// When sample `index` decodes, in the track's timescale, counted from 0.
    long decodeTime(int index) {
        return decodeTimes[index];
    }

    /// When sample `index` is presented: its decode time plus its `ctts` offset.
    long presentationTime(int index) {
        return decodeTimes[index] + compositionOffsets[index];
    }

    /// How long sample `index` plays, in the track's timescale.
    int duration(int index) {
        return durations[index];
    }

    /// Whether decoding can start at sample `index`.
    boolean isSync(int index) {
        return sync.get(index);
    }

    /// The time the last sample ends at: the sum of the `stts` deltas.
    long totalDuration() {
        var last = count() - 1;
        return last < 0 ? 0 : decodeTimes[last] + durations[last];
    }

    /// The last sync sample presented at or before `time`, in the track's
    /// timescale; the first sync sample when none is, and 0 for a track with no
    /// sync sample at all. Where a seek lands.
    int lastSyncAtOrBefore(long time) {
        var best = -1;
        var firstSync = -1;
        for (var i = 0; i < count(); i++) {
            if (!sync.get(i)) {
                continue;
            }
            if (firstSync < 0) {
                firstSync = i;
            }
            if (presentationTime(i) <= time) {
                best = i;
            }
        }
        return best >= 0 ? best : Math.max(firstSync, 0);
    }

    /// The last sample presented at or before `time`, or 0 when none is. Where
    /// a track that is not the one seeked by goes to.
    int lastAtOrBefore(long time) {
        var best = 0;
        for (var i = 0; i < count(); i++) {
            if (presentationTime(i) <= time) {
                best = i;
            }
        }
        return best;
    }

    /// This table with `other`'s samples after it: a fragment's samples
    /// appended to a track's.
    SampleTable append(SampleTable other) {
        if (other.count() == 0) {
            return this;
        }
        if (count() == 0) {
            return other;
        }
        var n = count() + other.count();
        var allOffsets = new long[n];
        var allSizes = new int[n];
        var allDecodeTimes = new long[n];
        var allCompositionOffsets = new int[n];
        var allDurations = new int[n];
        System.arraycopy(offsets, 0, allOffsets, 0, count());
        System.arraycopy(other.offsets, 0, allOffsets, count(), other.count());
        System.arraycopy(sizes, 0, allSizes, 0, count());
        System.arraycopy(other.sizes, 0, allSizes, count(), other.count());
        System.arraycopy(decodeTimes, 0, allDecodeTimes, 0, count());
        System.arraycopy(other.decodeTimes, 0, allDecodeTimes, count(), other.count());
        System.arraycopy(compositionOffsets, 0, allCompositionOffsets, 0, count());
        System.arraycopy(other.compositionOffsets, 0, allCompositionOffsets, count(), other.count());
        System.arraycopy(durations, 0, allDurations, 0, count());
        System.arraycopy(other.durations, 0, allDurations, count(), other.count());
        var allSync = (BitSet) sync.clone();
        for (var i = other.sync.nextSetBit(0); i >= 0; i = other.sync.nextSetBit(i + 1)) {
            allSync.set(count() + i);
        }
        return new SampleTable(allOffsets, allSizes, allDecodeTimes, allCompositionOffsets, allDurations, allSync);
    }

    /// A table from rows already flat: a fragment's `trun`.
    ///
    /// @param offsets            each sample's first byte
    /// @param sizes              each sample's length
    /// @param decodeTimes        each sample's decode time
    /// @param compositionOffsets each sample's presentation offset
    /// @param durations          each sample's duration
    /// @param sync               which samples are sync samples
    static SampleTable ofRows(
            long[] offsets, int[] sizes, long[] decodeTimes, int[] compositionOffsets, int[] durations, BitSet sync) {
        return new SampleTable(offsets, sizes, decodeTimes, compositionOffsets, durations, sync);
    }

    /// A run of `stts`: `count` samples, each `delta` long.
    record TimeRun(long count, long delta) {}

    /// A run of `ctts`: `count` samples, each presented `offset` after it
    /// decodes.
    record OffsetRun(long count, int offset) {}

    /// A run of `stsc`: from chunk `firstChunk` (counted from 1) on, each
    /// chunk holds `samplesPerChunk` samples.
    record ChunkRun(long firstChunk, long samplesPerChunk) {}

    /// The `stbl` boxes as they are read, in any order, flattened by
    /// [#build()].
    static final class Builder {

        private final List<TimeRun> timeRuns = new ArrayList<>();
        private final List<OffsetRun> offsetRuns = new ArrayList<>();
        private final List<ChunkRun> chunkRuns = new ArrayList<>();
        private int @Nullable [] sizes;
        private long @Nullable [] chunkOffsets;
        private long @Nullable [] syncSamples;

        void timeRun(long count, long delta) {
            timeRuns.add(new TimeRun(count, delta));
        }

        void offsetRun(long count, int offset) {
            offsetRuns.add(new OffsetRun(count, offset));
        }

        void chunkRun(long firstChunk, long samplesPerChunk) {
            chunkRuns.add(new ChunkRun(firstChunk, samplesPerChunk));
        }

        /// `stsz` or `stz2`, one length per sample.
        void sizes(int[] sizes) {
            this.sizes = sizes;
        }

        /// `stco` or `co64`, one offset per chunk.
        void chunkOffsets(long[] offsets) {
            this.chunkOffsets = offsets;
        }

        /// `stss`, the sync samples counted from 1. Absent, every sample is one.
        void syncSamples(long[] samples) {
            this.syncSamples = samples;
        }

        /// Whether any sample was declared.
        boolean hasSamples() {
            var declared = sizes;
            return declared != null && declared.length > 0;
        }

        /// The flat table.
        ///
        /// @throws MediaException [MediaError.InvalidData] when the boxes do not
        ///                        agree on how many samples there are
        SampleTable build() {
            var sampleSizes = sizes;
            var chunks = chunkOffsets;
            if (sampleSizes == null || sampleSizes.length == 0 || chunks == null) {
                return EMPTY;
            }
            var n = sampleSizes.length;
            var decodeTimes = new long[n];
            var durations = new int[n];
            var at = 0;
            var time = 0L;
            var lastDelta = 0L;
            for (var run : timeRuns) {
                for (var i = 0L; i < run.count() && at < n; i++) {
                    decodeTimes[at] = time;
                    durations[at] = clampDuration(run.delta());
                    time += run.delta();
                    at++;
                }
                lastDelta = run.delta();
            }
            // Samples the stts does not reach keep the last delta: a cut table
            // still plays at its rate.
            for (; at < n; at++) {
                decodeTimes[at] = time;
                durations[at] = clampDuration(lastDelta);
                time += lastDelta;
            }
            var compositionOffsets = new int[n];
            at = 0;
            for (var run : offsetRuns) {
                for (var i = 0L; i < run.count() && at < n; i++) {
                    compositionOffsets[at++] = run.offset();
                }
            }
            var offsets = new long[n];
            at = 0;
            for (var r = 0; r < chunkRuns.size() && at < n; r++) {
                var run = chunkRuns.get(r);
                var lastChunk = r + 1 < chunkRuns.size() ? chunkRuns.get(r + 1).firstChunk() - 1 : chunks.length;
                lastChunk = Math.min(lastChunk, chunks.length);
                for (var chunk = run.firstChunk(); chunk <= lastChunk && at < n; chunk++) {
                    if (chunk < 1) {
                        throw new MediaException(new MediaError.InvalidData("an stsc run starting at chunk " + chunk));
                    }
                    var position = chunks[(int) (chunk - 1)];
                    for (var i = 0L; i < run.samplesPerChunk() && at < n; i++) {
                        offsets[at] = position;
                        position += sampleSizes[at];
                        at++;
                    }
                }
            }
            if (at < n) {
                throw new MediaException(
                        new MediaError.InvalidData("the chunk tables place " + at + " of " + n + " samples"));
            }
            var sync = new BitSet(n);
            var declaredSync = syncSamples;
            if (declaredSync == null) {
                sync.set(0, n);
            } else {
                for (var sample : declaredSync) {
                    if (sample >= 1 && sample <= n) {
                        sync.set((int) (sample - 1));
                    }
                }
            }
            return new SampleTable(offsets, sampleSizes, decodeTimes, compositionOffsets, durations, sync);
        }

        private static int clampDuration(long delta) {
            return (int) Math.min(Math.max(delta, 0), Integer.MAX_VALUE);
        }
    }
}
