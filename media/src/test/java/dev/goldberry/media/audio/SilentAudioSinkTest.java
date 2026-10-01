package dev.goldberry.media.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The sink with no device, on a clock moved by hand: it has to drain exactly as
/// a device would, because the audio clock is made from what it reports.
@DisplayName("SilentAudioSink")
class SilentAudioSinkTest {

    private static final AudioFormat FORMAT = new AudioFormat(48_000, 2);
    private static final long MILLISECOND = 1_000_000L;

    private final AtomicLong now = new AtomicLong(1_000 * MILLISECOND);
    private final SilentAudioSink sink = new SilentAudioSink(now::get);

    @Test
    @DisplayName("takes the format asked for, and plays what is written at the sample rate")
    void drainsAtTheSampleRate() {
        assertEquals(FORMAT, sink.open(FORMAT));
        sink.write(MemorySegment.NULL, 48_000);
        assertEquals(48_000, sink.queuedSamples());

        now.addAndGet(250 * MILLISECOND);
        assertEquals(36_000, sink.queuedSamples());
        now.addAndGet(500 * MILLISECOND);
        assertEquals(12_000, sink.queuedSamples());
    }

    @Test
    @DisplayName("stands still once dry, and plays what comes next from when it comes")
    void neverRunsAheadOfWhatWasWritten() {
        sink.open(FORMAT);
        sink.write(MemorySegment.NULL, 4_800);
        now.addAndGet(1_000 * MILLISECOND);
        assertEquals(0, sink.queuedSamples());

        sink.write(MemorySegment.NULL, 4_800);
        assertEquals(4_800, sink.queuedSamples(), "the second of dry time was not credited to new samples");
        now.addAndGet(50 * MILLISECOND);
        assertEquals(2_400, sink.queuedSamples());
    }

    @Test
    @DisplayName("holds its queue while paused, and drops it on clear")
    void pausesAndClears() {
        sink.open(FORMAT);
        sink.pause();
        sink.write(MemorySegment.NULL, 9_600);
        now.addAndGet(1_000 * MILLISECOND);
        assertEquals(9_600, sink.queuedSamples());

        sink.resume();
        now.addAndGet(100 * MILLISECOND);
        assertEquals(4_800, sink.queuedSamples());

        sink.clear();
        assertEquals(0, sink.queuedSamples());
    }

    @Test
    @DisplayName("plays faster at a higher rate, and refuses what a device sink refuses")
    void followsTheRate() {
        sink.open(FORMAT);
        assertTrue(sink.setRate(2f));
        sink.write(MemorySegment.NULL, 48_000);
        now.addAndGet(250 * MILLISECOND);
        assertEquals(24_000, sink.queuedSamples());

        assertFalse(sink.setRate(0f));
        assertFalse(sink.setRate(Float.NaN));
        assertFalse(sink.setRate(SdlAudioSink.MAX_RATE * 2));
    }

    @Test
    @DisplayName("is heard at once, and cannot be written before it is open or opened twice")
    void lifecycle() {
        assertEquals(0, sink.queuedSamples());
        assertThrows(IllegalStateException.class, () -> sink.write(MemorySegment.NULL, 1));
        sink.open(FORMAT);
        assertEquals(0, sink.latencyNanos());
        assertThrows(IllegalStateException.class, () -> sink.open(FORMAT));
        sink.close();
        assertEquals(0, sink.queuedSamples());
    }
}
