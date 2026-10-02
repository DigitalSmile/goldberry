package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaClock;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.ffi.FfmpegLibraries;
import dev.goldberry.media.ffi.Hardware;
import dev.goldberry.media.io.Source;

/// The position while a seek waits to run: the target, whatever the audio
/// thread writes from before it.
///
/// The playback is never started, so no thread races the test: it plays the
/// audio thread's part itself, with the calls that thread makes.
@DisplayName("Playback, the position while a seek waits")
class SeekTargetTest {

    private static final AudioFormat FORMAT = AudioFormat.DEFAULT;
    private static final long TARGET = Duration.ofMillis(500).toNanos();

    private Playback playback;

    @BeforeEach
    void open() {
        FfmpegRequirement.enforce();
        playback = new Playback(
                FfmpegLibraries.get(),
                Source.of(URI.create("mem:///never-opened.webm")),
                List.of(),
                List.of(),
                Hardware.of(HardwareDecoding.OFF),
                new VirtualSink(FORMAT, false),
                MediaClock.system(),
                Playback.HIGH_WATER_NANOS,
                changed -> {});
    }

    @AfterEach
    void close() {
        if (playback != null) {
            playback.close();
        }
    }

    @Test
    @DisplayName("audio from before a seek that has not run yet does not take the position back from the target")
    void aStaleWriteKeepsTheTarget() {
        playback.seek(TARGET, true);
        // Half a millisecond of the old position's sound, written as the seek was asked for.
        playback.audioWritten(FORMAT.samples(500_000), true);
        assertEquals(TARGET, playback.positionNanos());
    }

    @Test
    @DisplayName("a seek asked for while another waits shows its own target, through the same stale audio")
    void theLatestTargetHolds() {
        playback.seek(TARGET, true);
        playback.audioWritten(FORMAT.samples(500_000), true);
        playback.seek(2 * TARGET, false);
        playback.audioWritten(FORMAT.samples(1_000_000), true);
        assertEquals(2 * TARGET, playback.positionNanos());
    }
}
