package io.github.digitalsmile.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
