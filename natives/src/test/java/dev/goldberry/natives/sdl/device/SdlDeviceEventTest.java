package dev.goldberry.natives.sdl.device;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlEventBuffer;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.SdlVideo;
import dev.goldberry.natives.sdl.event.SdlEventType;

/// SDL's device events read out of the buffer, and heard through a watch.
///
/// The first three tests fabricate the event at the offsets [Layouts] gives,
/// which the layout probe has checked against C, so they load no library. The
/// last pushes one through SDL's own queue, which is the route a plugged-in
/// headset takes.
///
/// [Layouts]: dev.goldberry.natives.layout.Layouts
@DisplayName("SdlDeviceEvent")
class SdlDeviceEventTest {

    @Test
    @DisplayName("each audio event keeps its device and which list it is in")
    void audio() {
        try (var buffer = new SdlEventBuffer()) {
            buffer.writeAudioDevice(SdlEventType.AUDIO_DEVICE_ADDED, 7, true);
            var added = SdlDeviceEvent.of(buffer);
            buffer.writeAudioDevice(SdlEventType.AUDIO_DEVICE_REMOVED, 8, false);
            var removed = SdlDeviceEvent.of(buffer);
            buffer.writeAudioDevice(SdlEventType.AUDIO_DEVICE_FORMAT_CHANGED, 9, true);
            var changed = SdlDeviceEvent.of(buffer);
            assertAll(
                    () -> assertEquals(Optional.of(new SdlDeviceEvent.AudioAdded(7, true)), added),
                    () -> assertEquals(Optional.of(new SdlDeviceEvent.AudioRemoved(8, false)), removed),
                    () -> assertEquals(Optional.of(new SdlDeviceEvent.AudioFormatChanged(9, true)), changed));
        }
    }

    @Test
    @DisplayName("camera events read their id from the camera arm")
    void camera() {
        try (var buffer = new SdlEventBuffer()) {
            var read = new ArrayList<Optional<SdlDeviceEvent>>();
            for (var type : List.of(
                    SdlEventType.CAMERA_DEVICE_ADDED,
                    SdlEventType.CAMERA_DEVICE_REMOVED,
                    SdlEventType.CAMERA_DEVICE_APPROVED,
                    SdlEventType.CAMERA_DEVICE_DENIED)) {
                buffer.writeCameraDevice(type, 42);
                read.add(SdlDeviceEvent.of(buffer));
            }
            assertEquals(
                    List.of(
                            Optional.of(new SdlDeviceEvent.CameraAdded(42)),
                            Optional.of(new SdlDeviceEvent.CameraRemoved(42)),
                            Optional.of(new SdlDeviceEvent.CameraApproved(42)),
                            Optional.of(new SdlDeviceEvent.CameraDenied(42))),
                    read);
        }
    }

    @Test
    @DisplayName("every other event is none")
    void others() {
        try (var buffer = new SdlEventBuffer()) {
            buffer.writeWindowEvent(SdlEventType.WINDOW_RESIZED, 1, 10, 10);
            var window = SdlDeviceEvent.of(buffer);
            buffer.writeWindowEvent(SdlEventType.USER, 0, 0, 0);
            var user = SdlDeviceEvent.of(buffer);
            assertAll(() -> assertEquals(Optional.empty(), window), () -> assertEquals(Optional.empty(), user));
        }
    }

    @Test
    @DisplayName("a watch hears a device event pushed through SDL's queue, and nothing after it is closed")
    void watch() {
        NativeLibraryRequirement.enforce();
        var sdl = Sdl.get();
        sdl.initializeSubsystems(List.of(SdlSubsystem.EVENTS));
        var heard = new CopyOnWriteArrayList<SdlDeviceEvent>();
        try (var buffer = new SdlEventBuffer()) {
            try (var _ = SdlDeviceWatch.install(heard::add)) {
                buffer.writeAudioDevice(SdlEventType.AUDIO_DEVICE_ADDED, 5, true);
                SdlVideo.get().push(buffer);
                buffer.writeWindowEvent(SdlEventType.WINDOW_EXPOSED, 1, 0, 0);
                SdlVideo.get().push(buffer);
            }
            buffer.writeCameraDevice(SdlEventType.CAMERA_DEVICE_ADDED, 6);
            SdlVideo.get().push(buffer);
        } finally {
            sdl.quitSubsystems(List.of(SdlSubsystem.EVENTS));
        }
        assertEquals(List.of(new SdlDeviceEvent.AudioAdded(5, true)), heard);
    }
}
