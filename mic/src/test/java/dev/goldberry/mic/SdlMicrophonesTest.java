package dev.goldberry.mic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlSubsystem;

/// The real backend, on SDL's `disk` driver, whose recording device reads the
/// file `SDL_AUDIO_DISK_INPUT_FILE` names and then records silence: a known
/// ramp goes in, and comes out of the recording's listeners and its pull
/// buffer frame for frame.
@DisplayName("Microphones, through SDL's disk driver")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SdlMicrophonesTest {

    private static final int FRAMES = 4_800;

    @TempDir
    Path directory;

    @BeforeEach
    void diskDriver() throws IOException {
        NativeLibraryRequirement.enforce();
        assumeFalse(Sdl.get().wasInit().contains(SdlSubsystem.AUDIO), "something else holds the audio subsystem");
        var input = directory.resolve("microphone.raw");
        var samples = ByteBuffer.allocate(FRAMES * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (var frame = 0; frame < FRAMES; frame++) {
            samples.putFloat(ramp(frame));
        }
        Files.write(input, samples.array());
        Sdl.get().setHint("SDL_AUDIO_DRIVER", "disk");
        Sdl.get().setHint("SDL_AUDIO_DISK_INPUT_FILE", input.toString());
    }

    @AfterEach
    void defaultDriver() {
        Sdl.get().setHint("SDL_AUDIO_DRIVER", "");
        Sdl.get().setHint("SDL_AUDIO_DISK_INPUT_FILE", "");
    }

    private static float ramp(int frame) {
        return (frame % 200) / 200f - 0.5f;
    }

    @Test
    @DisplayName("lists the disk driver's device and records the file, to listeners and to a puller alike")
    void recordsTheFile() throws Exception {
        try (var microphones = Microphones.open()) {
            assertFalse(microphones.list().isEmpty());
            var heard = new float[FRAMES];
            var filled = new AtomicLong();
            try (var recording = microphones.record(microphones.list().getFirst(), RecordingSpec.voice())) {
                recording.onSamples(block -> {
                    for (var frame = 0; frame < block.frames() && block.position() + frame < FRAMES; frame++) {
                        heard[(int) block.position() + frame] = block.sample(frame, 0);
                    }
                    filled.set(Math.min(FRAMES, block.position() + block.frames()));
                });
                while (filled.get() < FRAMES) {
                    LockSupport.parkNanos(5_000_000);
                }
                var pulled = FloatBuffer.allocate(FRAMES);
                while (pulled.hasRemaining()) {
                    if (recording.read(pulled) == 0) {
                        LockSupport.parkNanos(5_000_000);
                    }
                }
                for (var frame = 0; frame < FRAMES; frame++) {
                    assertEquals(ramp(frame), heard[frame], 1e-6, "heard frame " + frame);
                    assertEquals(ramp(frame), pulled.get(frame), 1e-6, "pulled frame " + frame);
                }
                assertAll(
                        () -> assertInstanceOf(RecordingStatus.Active.class, recording.status()),
                        () -> assertEquals(0, recording.droppedFrames()));
            }
        }
        assertFalse(Sdl.get().wasInit().contains(SdlSubsystem.AUDIO), "every reference was released");
    }
}
