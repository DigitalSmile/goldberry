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
        try (var fast = new SdlAudioSink();
                var plain = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            // Before opening: kept, and applied when the stream opens.
            assertTrue(fast.setRate(4f));
            assertFalse(fast.setRate(0.001f));
            assertFalse(fast.setRate(200f));
            fast.open(FORMAT);
            plain.open(FORMAT);
            var samples = 2 * FORMAT.sampleRate();
            var silence = arena.allocate(JAVA_FLOAT, (long) samples * FORMAT.channels());
            fast.write(silence, samples);
            plain.write(silence, samples);
            // Against a stream at 1 on the same device, not against the wall
            // clock. Both are SDL's dummy device, whose one thread takes a buffer
            // from each stream in the same pass, so at four times the speed the
            // fast queue drains four buffers for the plain one's one however the
            // runner schedules that thread. A wall-clock rate is the scheduler's:
            // a starved Windows runner measured 1.34 at four. Two is the line
            // between one and four; it is read once the plain stream has given
            // up four buffers, so a pull landing between the two readings cannot
            // decide it.
            var deadline = TimeBudget.of(Duration.ofSeconds(2)).deadlineFrom(System.nanoTime());
            long drainedFast = 0;
            long drainedPlain = 0;
            while (drainedPlain < 4 * 1024 && fast.queuedSamples() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
                drainedFast = samples - fast.queuedSamples();
                drainedPlain = samples - plain.queuedSamples();
            }
            var ratio = drainedPlain == 0 ? 0 : (double) drainedFast / drainedPlain;
            assertTrue(drainedPlain > 0, "the plain stream played nothing in the time allowed");
            assertTrue(
                    ratio >= 2,
                    "at 4 the queue drained " + ratio + "x what the same device drained at 1 (" + drainedFast + " / "
                            + drainedPlain + " samples)");
            assertTrue(fast.setRate(1f));
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
            // A pull is 1024 samples, about every 21 ms, and SDL's own queue steps
            // by one at each. The sink's estimate moves **between** them, so what
            // is asserted is readings where the sink's queue moved and SDL's did
            // not: a stepping queue never has one, however its readings fall.
            // Not a share of readings that moved: that is the scheduler's. On a
            // loaded machine the device pulls late, the estimate rightly stalls a
            // pull ahead of it, and a starved run moved 10 of 39. The deadline
            // only bounds a failure, so it runs to just short of the two seconds
            // queued: a starved runner has slept 90 ms in a `sleep(1)`.
            var last = Long.MAX_VALUE;
            var lastRaw = Long.MAX_VALUE;
            var readings = 0;
            var between = 0;
            var deadline = TimeBudget.of(Duration.ofMillis(1500))
                    .shortOf(Duration.ofSeconds(2))
                    .deadlineFrom(System.nanoTime());
            while (readings < 40 && sink.queuedSamples() > 0 && System.nanoTime() < deadline) {
                var rawBefore = sink.rawQueuedSamples();
                var queued = sink.queuedSamples();
                var raw = sink.rawQueuedSamples();
                assertTrue(queued <= last, "the queue rose from " + last + " to " + queued + " with nothing written");
                // A pull landing between the two raw readings is neither.
                if (queued < last && last != Long.MAX_VALUE && rawBefore == raw && raw == lastRaw) {
                    between++;
                }
                last = queued;
                lastRaw = raw;
                readings++;
                Thread.sleep(1);
            }
            var why = sink.queuedSamples() > 0 ? "before the deadline" : "before the queue ran dry";
            assertTrue(readings >= 10, "only " + readings + " readings " + why);
            assertTrue(
                    between >= 3,
                    between + " of " + (readings - 1) + " readings moved while SDL's queue stood still;"
                            + " a stepping queue moves only when the device pulls");

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

            // Let the dummy device take a pull, which says how big one is. The
            // loop ends at the first one; the deadline only bounds a device that
            // never pulls, and a starved runner has gone 300 ms between pulls.
            var deadline = TimeBudget.of(Duration.ofSeconds(2)).deadlineFrom(System.nanoTime());
            while (sink.latencyNanos() == Duration.ofMillis(100).toNanos() && System.nanoTime() < deadline) {
                sink.queuedSamples();
                Thread.sleep(2);
            }
            var sdl = sink.latencyNanos() - Duration.ofMillis(100).toNanos();
            var pulls = SdlAudioSink.pullsAhead(System.getProperty("os.name", ""));
            assertTrue(sdl > 0, "no pull was seen");
            // A pull is SDL's device buffer, about 1024 frames at 48 kHz, 21 ms. It
            // is counted in samples rather than timed, but a reader that polls
            // late can see several pulls land as one -- a starved Windows runner
            // saw eight, 170 ms -- so the window is wide: between 2 and 500 ms is
            // a pull, and a count of them is what is reported. A latency of
            // nothing, or of a second, is still outside it.
            var perPull = sdl / pulls;
            assertTrue(
                    perPull >= 2_000_000L && perPull <= 500_000_000L,
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
