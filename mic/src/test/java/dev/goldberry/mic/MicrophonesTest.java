package dev.goldberry.mic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.backend.CaptureDevice;
import dev.goldberry.mic.backend.DeviceEvent;

/// The device list, the system's choice and hot-plug, over the fake backend.
@DisplayName("Microphones")
class MicrophonesTest {

    @Test
    @DisplayName("lists the devices, and names the system's choice apart from them")
    void lists() {
        var backend = new FakeBackend(new CaptureDevice(3, "Built-in"), new CaptureDevice(9, "Headset"));
        try (var microphones = new Microphones(backend)) {
            var listed = microphones.list();
            var chosen = microphones.systemDefault();
            assertAll(
                    () -> assertEquals(
                            List.of("Built-in", "Headset"),
                            listed.stream().map(Microphone::name).toList()),
                    () -> assertFalse(listed.getFirst().isSystemDefault()),
                    () -> assertTrue(chosen.isSystemDefault()),
                    () -> assertEquals("Default Recording Device", chosen.name()),
                    () -> assertFalse(listed.contains(chosen)),
                    () -> assertEquals(listed, microphones.list(), "the same devices are equal"));
        }
    }

    @Test
    @DisplayName("tells listeners what is plugged in and taken away, by name")
    void hotPlug() throws Exception {
        var backend = new FakeBackend(new CaptureDevice(3, "Built-in"));
        try (var microphones = new Microphones(backend)) {
            microphones.list();
            var changes = new CopyOnWriteArrayList<String>();
            var subscription = microphones.onChange(change -> changes.add(
                    switch (change) {
                        case MicrophoneChange.Added(var microphone) -> "+" + microphone.name();
                        case MicrophoneChange.Removed(var microphone) -> "-" + microphone.name();
                    }));
            backend.raise(new DeviceEvent.Added(11));
            backend.raise(new DeviceEvent.Removed(3));
            backend.raise(new DeviceEvent.Removed(11));
            subscription.close();
            backend.raise(new DeviceEvent.Added(12));
            assertEquals(List.of("+Headset 11", "-Built-in", "-Headset 11"), changes);
        }
    }

    @Test
    @DisplayName("refuses a device that is gone, and everything once closed, which releases the backend")
    void refuses() throws Exception {
        var backend = new FakeBackend(new CaptureDevice(3, "Built-in"));
        var microphones = new Microphones(backend);
        var built = microphones.list().getFirst();
        backend.devices.clear();
        assertThrows(MicrophoneException.class, () -> microphones.record(built, RecordingSpec.voice()));
        microphones.close();
        microphones.close();
        assertAll(
                () -> assertTrue(backend.closed),
                () -> assertEquals(0, backend.watchers()),
                () -> assertThrows(IllegalStateException.class, microphones::list));
    }
}
