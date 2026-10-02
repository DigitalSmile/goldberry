package dev.goldberry.media.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.audio.SdlAudioStream;

/// The desktop sink on a machine with no audio device: it plays into
/// silence rather than failing the source. SDL is never reached, so this needs
/// no `libgoldberry`.
@DisplayName("SdlAudioSink, with no audio device")
class SdlAudioSinkFallbackTest {

    private static final AudioFormat FORMAT = new AudioFormat(48_000, 2);
    private static final long MILLISECOND = 1_000_000L;

    private final AtomicLong now = new AtomicLong(1_000 * MILLISECOND);
    private final AtomicInteger attempts = new AtomicInteger();

    private SdlAudioStream noDevice(int sampleRate, int channels) {
        attempts.incrementAndGet();
        throw new SdlException("SDL_InitSubSystem", "No available audio device");
    }

    private SdlAudioSink sink() {
        return new SdlAudioSink(OutputLatency.NONE, this::noDevice, now::get);
    }

    @Test
    @DisplayName("opens anyway, with the format asked for, and plays into silence at the sample rate")
    void playsIntoSilence() {
        try (var sink = sink()) {
            assertEquals(FORMAT, sink.open(FORMAT));
            assertTrue(sink.silent());
            sink.write(MemorySegment.NULL, 48_000);
            now.addAndGet(500 * MILLISECOND);
            assertEquals(24_000, sink.queuedSamples());
            assertEquals(0, sink.latencyNanos());
        }
    }

    @Test
    @DisplayName("pauses, clears and changes rate as it would with a device")
    void controlsReachTheSilence() {
        try (var sink = sink()) {
            assertTrue(sink.setRate(2f), "a rate set before the open is kept");
            sink.open(FORMAT);
            sink.pause();
            sink.write(MemorySegment.NULL, 48_000);
            now.addAndGet(1_000 * MILLISECOND);
            assertEquals(48_000, sink.queuedSamples(), "paused");

            sink.resume();
            now.addAndGet(250 * MILLISECOND);
            assertEquals(24_000, sink.queuedSamples(), "at twice the rate");

            assertFalse(sink.setRate(0f));
            sink.setGain(0.5f);
            sink.clear();
            assertEquals(0, sink.queuedSamples());
        }
    }

    @Test
    @DisplayName("tries the device again on every open, so a device that appears is used")
    void triesTheDeviceEachOpen() {
        var sink = sink();
        sink.open(FORMAT);
        assertThrows(IllegalStateException.class, () -> sink.open(FORMAT));
        sink.close();
        assertFalse(sink.silent());
        sink.open(FORMAT);
        sink.close();
        assertEquals(2, attempts.get());
    }

    @Test
    @DisplayName("lets a failure other than SDL's through")
    void onlySdlFailuresFallBack() {
        var sink = new SdlAudioSink(
                OutputLatency.NONE,
                (_, _) -> {
                    throw new IllegalArgumentException("bad channel count");
                },
                now::get);
        assertThrows(IllegalArgumentException.class, () -> sink.open(FORMAT));
        assertFalse(sink.silent());
    }
}
