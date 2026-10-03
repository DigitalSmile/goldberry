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

import dev.goldberry.junit.TimeBudget;
import dev.goldberry.natives.NativeLibrary;

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
    @DisplayName("plays faster at a rate, and refuses one SDL does not take")
    void rate() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            // Before opening: kept, and applied when the stream opens.
            assertTrue(sink.setRate(4f));
            assertFalse(sink.setRate(0.001f));
            assertFalse(sink.setRate(200f));
            sink.open(FORMAT);
            var samples = 2 * FORMAT.sampleRate();
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            // The rate the queue drains at, against the wall clock, read until it
            // shows. At four times the speed it is four; at one it would be one,
            // and two is the line between them. Read over a whole run rather than
            // once after a fixed sleep, so a runner that is slow to schedule the
            // device's thread is waited for instead of failed: only what has been
            // drained past the first tenth of a second counts, because the
            // device's first pull takes a buffer at once at any rate.
            var started = System.nanoTime();
            var deadline = TimeBudget.of(Duration.ofSeconds(2)).deadlineFrom(started);
            var observed = 0.0;
            while (observed < 2 && sink.queuedSamples() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
                var drained = samples - sink.queuedSamples();
                var elapsed = (System.nanoTime() - started) * (double) FORMAT.sampleRate() / 1e9;
                if (drained >= FORMAT.sampleRate() / 10) {
                    observed = Math.max(observed, drained / elapsed);
                }
            }
            assertTrue(observed >= 2, "the queue drained at " + observed + "x the wall clock, asked for 4x");
            assertTrue(sink.setRate(1f));
        }
    }

    @Test
    @DisplayName("drains smoothly between the device's pulls, never rising, and stands still while paused")
    void drainsSmoothly() throws InterruptedException {
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(FORMAT);
            var samples = FORMAT.sampleRate() * 2;
            sink.write(arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels()), samples);
            // A pull is 1024 samples, about every 21 ms. Stepping alone would leave
            // most readings a millisecond apart equal to the one before; the
            // estimate moves between them. So what is asserted is the share of
            // readings that moved, over at least forty of them, rather than a
            // count of distinct values in a fixed window -- a runner that sleeps
            // long between readings takes longer to collect them, and fails
            // nothing.
            var last = Long.MAX_VALUE;
            var readings = 0;
            var moved = 0;
            var deadline = TimeBudget.of(Duration.ofMillis(300)).deadlineFrom(System.nanoTime());
            while (readings < 40 && sink.queuedSamples() > 0 && System.nanoTime() < deadline) {
                var queued = sink.queuedSamples();
                assertTrue(queued <= last, "the queue rose from " + last + " to " + queued + " with nothing written");
                if (queued < last && last != Long.MAX_VALUE) {
                    moved++;
                }
                last = queued;
                readings++;
                Thread.sleep(1);
            }
            assertTrue(readings >= 10, "only " + readings + " readings before the queue ran dry");
            assertTrue(
                    moved * 2 > readings - 1,
                    moved + " of " + (readings - 1) + " readings moved; a stepping queue moves once a pull");

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
    /// [SdlAudioSink#REFRESH] however often the Engine writes.
    @Test
    @DisplayName("reports SDL's buffers and the system's latency, asking the system at most once a second")
    void latency() throws InterruptedException {
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

            // Let the dummy device take a pull, which says how big one is.
            var deadline = TimeBudget.of(Duration.ofMillis(500)).deadlineFrom(System.nanoTime());
            while (sink.latencyNanos() == Duration.ofMillis(100).toNanos() && System.nanoTime() < deadline) {
                sink.queuedSamples();
                Thread.sleep(2);
            }
            var sdl = sink.latencyNanos() - Duration.ofMillis(100).toNanos();
            var pulls = SdlAudioSink.pullsAhead(System.getProperty("os.name", ""));
            assertTrue(sdl > 0, "no pull was seen");
            // A pull is SDL's device buffer, about 1024 frames at 48 kHz, 21 ms. It
            // is counted in samples rather than timed, but a reader that polls
            // late can see two or three pulls land as one, so the window is wide:
            // between 2 and 100 ms is a pull, and a count of them is what is
            // reported. A latency of nothing, or of a second, is still outside it.
            var perPull = sdl / pulls;
            assertTrue(
                    perPull >= 2_000_000L && perPull <= 100_000_000L,
                    "SDL's part is " + sdl + " ns for " + pulls + " pulls");
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
