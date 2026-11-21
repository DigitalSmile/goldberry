package dev.goldberry.natives.sdl.device;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlEventBuffer;
import dev.goldberry.natives.sdl.event.SdlEventType;

/// An audio device or a camera came, went or changed: SDL's device events, read
/// out of the event they arrived in.
///
/// Sealed, so a listener switches over the seven and the compiler checks it.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public sealed interface SdlDeviceEvent {

    /// The `SDL_AudioDeviceID` or `SDL_CameraID` the event is about.
    int device();

    /// An audio device appeared.
    ///
    /// @param recording whether it is a recording device rather than a playback one
    record AudioAdded(int device, boolean recording) implements SdlDeviceEvent {}

    /// An audio device went away.
    ///
    /// @param recording whether it was a recording device
    record AudioRemoved(int device, boolean recording) implements SdlDeviceEvent {}

    /// The system changed an audio device's format.
    ///
    /// @param recording whether it is a recording device
    record AudioFormatChanged(int device, boolean recording) implements SdlDeviceEvent {}

    /// A camera appeared.
    record CameraAdded(int device) implements SdlDeviceEvent {}

    /// A camera went away.
    record CameraRemoved(int device) implements SdlDeviceEvent {}

    /// The user let an opened camera be used.
    record CameraApproved(int device) implements SdlDeviceEvent {}

    /// The user refused an opened camera.
    record CameraDenied(int device) implements SdlDeviceEvent {}

    /// The device event `event` holds, or empty for any other kind. Cheap for
    /// the others, which a watch sees for every event: two range checks.
    static Optional<SdlDeviceEvent> of(SdlEventBuffer event) {
        var type = event.type();
        var audio = type >= SdlEventType.AUDIO_DEVICE_ADDED.value()
                && type <= SdlEventType.AUDIO_DEVICE_FORMAT_CHANGED.value();
        var camera =
                type >= SdlEventType.CAMERA_DEVICE_ADDED.value() && type <= SdlEventType.CAMERA_DEVICE_DENIED.value();
        if (!audio && !camera) {
            return Optional.empty();
        }
        for (var kind : SdlEventType.values()) {
            if (kind.value() == type) {
                return Optional.ofNullable(decode(kind, event));
            }
        }
        return Optional.empty();
    }

    private static @Nullable SdlDeviceEvent decode(SdlEventType kind, SdlEventBuffer event) {
        var device = event.deviceId();
        return switch (kind) {
            case AUDIO_DEVICE_ADDED -> new AudioAdded(device, event.deviceIsRecording());
            case AUDIO_DEVICE_REMOVED -> new AudioRemoved(device, event.deviceIsRecording());
            case AUDIO_DEVICE_FORMAT_CHANGED -> new AudioFormatChanged(device, event.deviceIsRecording());
            case CAMERA_DEVICE_ADDED -> new CameraAdded(device);
            case CAMERA_DEVICE_REMOVED -> new CameraRemoved(device);
            case CAMERA_DEVICE_APPROVED -> new CameraApproved(device);
            case CAMERA_DEVICE_DENIED -> new CameraDenied(device);
            default -> null;
        };
    }
}
