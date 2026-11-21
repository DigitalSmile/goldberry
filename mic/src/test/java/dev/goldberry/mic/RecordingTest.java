package dev.goldberry.mic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.mic.backend.CaptureDevice;
import dev.goldberry.mic.backend.DeviceEvent;
import dev.goldberry.mic.source.SampleFormat;

/// A recording over the fake backend: blocks in order and whole, the pull
/// buffer beside them, pause and resume, loss and close. The capture thread
/// runs on its own schedule, so every wait is for a condition under @Timeout.
@DisplayName("Recording")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class RecordingTest {

    private static final SampleFormat STEREO = new SampleFormat(48_000, 2);

    private static void until(BooleanSupplier condition) {
        while (!condition.getAsBoolean()) {
            LockSupport.parkNanos(1_000_000);
        }
    }

    private static float[] ramp(int count) {
        var samples = new float[count];
        for (var index = 0; index < count; index++) {
            samples[index] = index / (float) count;
        }
        return samples;
    }

    @Test
    @DisplayName("hands over whole blocks in order, with their positions, and resumes the device on start")
    void blocks() {
        var backend = new FakeBackend();
        try (var microphones = new Microphones(backend);
                var recording = microphones.record(new RecordingSpec(STEREO, 4, RecordingSpec.BUFFER))) {
            var heard = new CopyOnWriteArrayList<float[]>();
            var positions = new CopyOnWriteArrayList<Long>();
            recording.onSamples(block -> {
                heard.add(block.copy());
                positions.add(block.position());
            });
            var stream = backend.opened.getFirst();
            assertEquals(List.of("resume"), stream.calls);
            var samples = ramp(24);
            stream.feed(samples);
            until(() -> heard.size() == 3);
            assertAll(
                    () -> assertArrayEquals(Arrays.copyOfRange(samples, 0, 8), heard.get(0)),
                    () -> assertArrayEquals(Arrays.copyOfRange(samples, 16, 24), heard.get(2)),
                    () -> assertEquals(List.of(0L, 4L, 8L), positions),
                    () -> assertEquals(12, recording.position()),
                    () -> assertInstanceOf(RecordingStatus.Active.class, recording.status()));
        }
    }

    @Test
    @DisplayName("keeps what was recorded for a caller that pulls, and drops the oldest past the buffer")
    void pulls() {
        var backend = new FakeBackend();
        var spec = new RecordingSpec(new SampleFormat(8_000, 1), 4, Duration.ofMillis(1));
        try (var microphones = new Microphones(backend);
                var recording = microphones.record(spec)) {
            var stream = backend.opened.getFirst();
            stream.feed(ramp(16));
            until(() -> recording.position() == 16);
            var into = FloatBuffer.allocate(16);
            assertAll(
                    () -> assertEquals(8, recording.availableFrames(), "a millisecond at 8 kHz is 8 frames"),
                    () -> assertEquals(8, recording.droppedFrames()),
                    () -> assertEquals(8, recording.read(into)),
                    () -> assertEquals(8 / 16f, into.get(0), "the oldest kept"),
                    () -> assertEquals(0, recording.availableFrames()));
        }
    }

    @Test
    @DisplayName("pauses and resumes the device, telling the status listeners each time")
    void pauseAndResume() {
        var backend = new FakeBackend();
        try (var microphones = new Microphones(backend);
                var recording = microphones.record(RecordingSpec.voice())) {
            var statuses = new CopyOnWriteArrayList<RecordingStatus>();
            recording.onStatus(statuses::add);
            recording.pause();
            recording.pause();
            recording.resume();
            recording.resume();
            assertEquals(List.of(new RecordingStatus.Paused(), new RecordingStatus.Active()), statuses);
            assertEquals(List.of("resume", "pause", "resume"), backend.opened.getFirst().calls);
        }
    }

    @Test
    @DisplayName("is lost when its pinned device is unplugged, and closes that device")
    void lostWhenUnplugged() {
        var backend = new FakeBackend(new CaptureDevice(7, "Headset"));
        try (var microphones = new Microphones(backend)) {
            var pinned = microphones.list().getFirst();
            var recording = microphones.record(pinned, RecordingSpec.voice());
            var following = microphones.record(RecordingSpec.voice());
            backend.raise(new DeviceEvent.Removed(7));
            until(() -> recording.status() instanceof RecordingStatus.Lost);
            var lost = (RecordingStatus.Lost) recording.status();
            assertAll(
                    () -> assertTrue(lost.reason().contains("Headset"), lost.reason()),
                    () -> assertTrue(backend.opened.getFirst().closed),
                    () -> assertInstanceOf(RecordingStatus.Active.class, following.status(), "follows the system"));
            recording.close();
            following.close();
            assertInstanceOf(RecordingStatus.Lost.class, recording.status(), "a lost recording stays lost");
            assertInstanceOf(RecordingStatus.Closed.class, following.status());
        }
    }

    @Test
    @DisplayName("is lost when the device fails a read")
    void lostWhenTheReadFails() {
        var backend = new FakeBackend();
        try (var microphones = new Microphones(backend);
                var recording = microphones.record(RecordingSpec.voice())) {
            backend.opened.getFirst().failing = true;
            until(() -> recording.status() instanceof RecordingStatus.Lost);
            assertTrue(recording.status().isFinal());
        }
    }

    @Test
    @DisplayName("carries on past a listener that throws, and closes from inside a listener")
    void listeners() {
        var backend = new FakeBackend();
        try (var microphones = new Microphones(backend)) {
            var recording = microphones.record(new RecordingSpec(SampleFormat.VOICE, 2, RecordingSpec.BUFFER));
            var heard = new CopyOnWriteArrayList<Long>();
            recording.onSamples(_ -> {
                throw new IllegalStateException("a listener's own bug");
            });
            recording.onSamples(block -> {
                heard.add(block.position());
                if (heard.size() == 2) {
                    recording.close();
                }
            });
            backend.opened.getFirst().feed(ramp(10));
            until(() -> recording.status() instanceof RecordingStatus.Closed);
            assertEquals(List.of(0L, 2L), heard);
            assertTrue(backend.opened.getFirst().closed);
        }
    }

    @Test
    @DisplayName("closes once, ends the capture thread, and says closed")
    void closes() {
        var backend = new FakeBackend();
        try (var microphones = new Microphones(backend)) {
            var recording = microphones.record(RecordingSpec.voice());
            recording.close();
            recording.close();
            assertEquals(List.of("resume", "close"), backend.opened.getFirst().calls);
            assertInstanceOf(RecordingStatus.Closed.class, recording.status());
        }
    }
}
