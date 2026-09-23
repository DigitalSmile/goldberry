package io.github.digitalsmile.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.foreign.Arena;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;

/// The desktop sink, against SDL's `dummy` driver (the test task sets
/// `SDL_AUDIO_DRIVER=dummy`): it consumes audio at the real rate and plays
/// nothing, so these tests are silent and need no sound card.
@DisplayName("SdlAudioSink, against SDL's dummy driver")
class SdlAudioSinkTest {

    private static final AudioFormat FORMAT = new AudioFormat(48_000, 2);

    @BeforeEach
    void requireLibgoldberry() {
        assumeTrue(NativeLibrary.isAvailable(), "libgoldberry is not built; run :natives:cmakeBuild");
    }

    @Test
    @DisplayName("opens with the format asked for, queues what is written, and clears it")
    void queues() {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            assertEquals(FORMAT, sink.open(FORMAT));
            sink.pause();
            var samples = FORMAT.sampleRate() / 10;
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            assertEquals(samples, sink.queuedSamples());
            sink.clear();
            assertEquals(0, sink.queuedSamples());
        }
    }

    @Test
    @DisplayName("plays what is queued once resumed, which is what moves the audio clock")
    void plays() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(FORMAT);
            var samples = FORMAT.sampleRate() / 5;
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            var deadline = System.nanoTime() + 3_000_000_000L;
            while (sink.queuedSamples() >= samples && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(sink.queuedSamples() < samples, "the device consumed nothing in three seconds");
        }
    }

    @Test
    @DisplayName("takes gain before and after opening, and refuses a write before it")
    void gainAndOrder() {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.setGain(0.25f);
            assertThrows(IllegalStateException.class, () -> sink.write(arena.allocate(8), 1));
            assertEquals(0, sink.queuedSamples());
            sink.open(FORMAT);
            sink.setGain(0f);
            sink.pause();
            sink.resume();
            assertThrows(IllegalStateException.class, () -> sink.open(FORMAT));
        }
    }

    @Test
    @DisplayName("plays faster at a rate, and refuses one SDL does not take")
    void rate() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            // Before opening: kept, and applied when the stream opens.
            assertTrue(sink.setRate(4f));
            assertFalse(sink.setRate(0.001f));
            assertFalse(sink.setRate(200f));
            sink.open(FORMAT);
            var samples = FORMAT.sampleRate();
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            Thread.sleep(250);
            // A quarter of a second at four times the speed is a second of audio;
            // at 1 it would be a quarter. Half a second is a safe margin either way.
            var drained = samples - sink.queuedSamples();
            assertTrue(drained > samples / 2, "drained only " + drained + " of " + samples + " in 250 ms at 4x");
            assertTrue(sink.setRate(1f));
        }
    }

    @Test
    @DisplayName("drains smoothly between the device's pulls, never rising, and stands still while paused")
    void drainsSmoothly() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(FORMAT);
            var samples = FORMAT.sampleRate() / 2;
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            var seen = new java.util.TreeSet<Long>();
            var last = Long.MAX_VALUE;
            var deadline = System.nanoTime() + 150_000_000L;
            while (System.nanoTime() < deadline) {
                var queued = sink.queuedSamples();
                assertTrue(queued <= last, "the queue rose from " + last + " to " + queued + " with nothing written");
                last = queued;
                seen.add(queued);
                Thread.sleep(1);
            }
            // A pull is 1024 samples, about every 21 ms: stepping alone would give
            // a handful of values in 150 ms, and the estimate gives many more.
            assertTrue(seen.size() > 20, "only " + seen.size() + " distinct readings: " + seen);

            sink.pause();
            var paused = sink.queuedSamples();
            Thread.sleep(40);
            assertEquals(paused, sink.queuedSamples(), "paused, nothing drains");
            sink.resume();
            sink.clear();
            assertEquals(0, sink.queuedSamples());
        }
    }
}
