package dev.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.foreign.Arena;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibrary;

/// The desktop sink, against SDL's `dummy` driver (the test task sets
/// `SDL_AUDIO_DRIVER=dummy`): it consumes audio at the real rate and plays
/// nothing, so these tests are silent and need no sound card.
///
/// The device's thread pulls on its own schedule, so nothing here compares a
/// measured duration, rate or count of readings with a bound: a `sleep(1)` loop
/// under a parallel build on a four-core runner read five times in a second and
/// a half, and a 4x stream drained at 1.34x the wall clock. What is asserted is
/// what holds however the device thread is scheduled: a queue that never rises
/// with nothing written, a paused queue that stands still, a rate SDL refuses.
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
            long queued = samples;
            var deadline = System.nanoTime() + 3_000_000_000L;
            while (sink.queuedSamples() >= queued && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(sink.queuedSamples() < queued, "the device consumed nothing in three seconds");
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
    @DisplayName("takes a rate before and after opening, and refuses one SDL does not take")
    void rate() {
        try (var sink = new SdlAudioSink()) {
            // Before opening: kept, and applied when the stream opens.
            assertTrue(sink.setRate(4f));
            assertFalse(sink.setRate(0.001f));
            assertFalse(sink.setRate(200f));
            sink.open(FORMAT);
            assertTrue(sink.setRate(1f));
            assertFalse(sink.setRate(200f));
        }
    }

    @Test
    @DisplayName("drains while playing, never rising, and stands still while paused")
    void drains() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(FORMAT);
            var samples = FORMAT.sampleRate() * 2;
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            // The estimate moves between the device's pulls, and SDL's own queue
            // steps at each. What is asserted is what every reading must satisfy
            // whenever it lands: the queue never rises with nothing written. How
            // many readings land before the deadline, and how many of them moved,
            // is the scheduler's, so no count is asserted; the deadline only ends
            // the loop.
            var last = Long.MAX_VALUE;
            var deadline = System.nanoTime() + Duration.ofMillis(1500).toNanos();
            while (sink.queuedSamples() > 0 && System.nanoTime() < deadline) {
                var queued = sink.queuedSamples();
                assertTrue(queued <= last, "the queue rose from " + last + " to " + queued + " with nothing written");
                last = queued;
                Thread.sleep(1);
            }

            sink.pause();
            var paused = sink.queuedSamples();
            Thread.sleep(40);
            assertEquals(paused, sink.queuedSamples(), "paused, nothing drains");
            sink.resume();
            sink.clear();
            assertEquals(0, sink.queuedSamples());
        }
    }

    @Test
    @DisplayName("counts three pulls of SDL's buffers on macOS, and the one the smoothing runs ahead elsewhere")
    void pullsAhead() {
        assertEquals(3, SdlAudioSink.pullsAhead("Mac OS X"));
        assertEquals(1, SdlAudioSink.pullsAhead("Linux"));
        assertEquals(1, SdlAudioSink.pullsAhead("Windows 11"));
    }

    /// The latency is SDL's buffers, counted in pulls of the size the device takes,
    /// plus what the system says, asked once on open and then at most once a
    /// [SdlAudioSink#REFRESH] however often the Engine writes. SDL's part is
    /// counted from the device's first pull, which lands on the device thread's
    /// schedule -- a starved runner went 300 ms without one -- so it is not read
    /// here; [#pullsAhead] holds the count it is made of.
    @Test
    @DisplayName("reports the system's latency, asked once on open and not again per write")
    void latency() {
        var asked = new int[1];
        OutputLatency system = () -> {
            asked[0]++;
            return Optional.of(Duration.ofMillis(100));
        };
        try (var sink = new SdlAudioSink(system);
                var arena = Arena.ofConfined()) {
            assertEquals(0, sink.latencyNanos(), "a sink not open has no latency");
            sink.open(FORMAT);
            assertEquals(1, asked[0]);
            assertEquals(Duration.ofMillis(100).toNanos(), sink.latencyNanos(), "no pull yet: the system's alone");

            var samples = FORMAT.sampleRate() / 100;
            for (var i = 0; i < 20; i++) {
                sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            }
            assertEquals(1, asked[0], "twenty writes in a moment ask the system once");
        }
    }

    @Test
    @DisplayName("with no system latency it counts SDL's buffers alone")
    void noSystemLatency() {
        try (var sink = new SdlAudioSink(OutputLatency.NONE)) {
            sink.open(FORMAT);
            assertEquals(0, sink.latencyNanos(), "no pull yet, and nothing from the system");
        }
    }
}
