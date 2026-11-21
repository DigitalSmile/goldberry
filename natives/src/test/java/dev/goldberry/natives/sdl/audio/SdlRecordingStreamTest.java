package dev.goldberry.natives.sdl.audio;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;

/// Recording, against SDL's `disk` driver: its recording device reads raw
/// samples from the file `SDL_AUDIO_DISK_INPUT_FILE` names, in the device's
/// format, and records silence after the end. So a known ramp written to the
/// file comes back out of the stream, which is a microphone CI can have.
///
/// Nothing compares a duration with a bound: the device thread delivers on its
/// own schedule, and the reads wait for the frames under a @Timeout.
@DisplayName("SdlRecordingStream, on SDL's disk driver")
class SdlRecordingStreamTest {

    private static final String AUDIO_DRIVER = "SDL_AUDIO_DRIVER";
    private static final String INPUT_FILE = "SDL_AUDIO_DISK_INPUT_FILE";
    private static final int RATE = 48_000;
    private static final int FRAMES = RATE / 10;

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
        Sdl.get().setHint(AUDIO_DRIVER, "disk");
        Sdl.get().setHint(INPUT_FILE, input.toString());
    }

    @AfterEach
    void defaultDriver() {
        Sdl.get().setHint(AUDIO_DRIVER, "");
        Sdl.get().setHint(INPUT_FILE, "");
    }

    private static float ramp(int frame) {
        return (frame % 100) / 100f - 0.5f;
    }

    @Test
    @DisplayName("lists the disk driver's recording device, with a name and a format")
    void lists() {
        try (var devices = SdlAudioDevices.open()) {
            var recording = devices.recordingDevices();
            assertFalse(recording.isEmpty(), "the disk driver has a recording device");
            var first = recording.getFirst();
            assertAll(
                    () -> assertFalse(first.name().isEmpty()),
                    () -> assertTrue(devices.format(first.id()).isPresent()),
                    () -> assertTrue(
                            devices.format(SdlAudioDevices.defaultRecording()).isPresent()),
                    () -> assertTrue(SdlAudioDevices.isDefaultRecording(SdlAudioDevices.defaultRecording())));
        }
        assertFalse(Sdl.get().wasInit().contains(SdlSubsystem.AUDIO), "the reference was released");
    }

    @Test
    @DisplayName("opens a listed device while the list is open, and refuses its id once it is not")
    void idsLastWhileTheListIsOpen() {
        int id;
        try (var devices = SdlAudioDevices.open()) {
            id = devices.recordingDevices().getFirst().id();
            try (var stream = SdlRecordingStream.open(id, RATE, 1)) {
                assertEquals(id, stream.device());
            }
        }
        assertThrows(
                SdlException.class, () -> SdlRecordingStream.open(id, RATE, 1).close());
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("reads the file back, frame for frame, once resumed")
    void recordsTheFile() throws InterruptedException {
        try (var stream = SdlRecordingStream.open(SdlAudioDevices.defaultRecording(), RATE, 1)) {
            var into = ByteBuffer.allocateDirect(FRAMES * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0, stream.read(into), "paused until resumed");
            stream.resume();
            while (into.hasRemaining()) {
                if (stream.read(into) == 0) {
                    Thread.sleep(5);
                }
            }
            into.flip();
            for (var frame = 0; frame < FRAMES; frame++) {
                assertEquals(ramp(frame), into.getFloat(), 1e-6, "frame " + frame);
            }
        }
    }

    @Test
    @DisplayName("reads whole frames only, and refuses a heap buffer and a playback id")
    void wholeFramesOnly() {
        assertThrows(IllegalArgumentException.class, () -> SdlRecordingStream.open(0, RATE, 1));
        assertThrows(IllegalArgumentException.class, () -> SdlRecordingStream.open(0xFFFFFFFF, RATE, 1));
        try (var stream = SdlRecordingStream.open(SdlAudioDevices.defaultRecording(), RATE, 2)) {
            assertEquals(2, stream.channels());
            assertEquals(RATE, stream.sampleRate());
            var tooSmall = ByteBuffer.allocateDirect(Float.BYTES);
            assertEquals(0, stream.read(tooSmall));
            assertEquals(0, tooSmall.position());
            assertThrows(IllegalArgumentException.class, () -> stream.read(ByteBuffer.allocate(64)));
        }
    }
}
