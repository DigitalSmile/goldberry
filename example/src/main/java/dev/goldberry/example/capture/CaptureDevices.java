package dev.goldberry.example.capture;

import java.util.Objects;
import java.util.function.Supplier;

import dev.goldberry.camera.CameraException;
import dev.goldberry.camera.Cameras;
import dev.goldberry.mic.MicrophoneException;
import dev.goldberry.mic.Microphones;

/// Where the Capture screen gets the system's microphones and cameras.
///
/// The window asks the system. A picture of the screen asks nothing, because
/// the build machine's devices are not something a golden may photograph, so
/// it is given [#none] instead, as it is given a fixed set of capabilities.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param microphones opens the microphones; may throw [MicrophoneException]
/// @param cameras     opens the cameras; may throw [CameraException]
public record CaptureDevices(Supplier<Microphones> microphones, Supplier<Cameras> cameras) {

    public CaptureDevices {
        Objects.requireNonNull(microphones, "microphones");
        Objects.requireNonNull(cameras, "cameras");
    }

    /// The system's own devices.
    public static CaptureDevices system() {
        return new CaptureDevices(Microphones::open, Cameras::open);
    }

    /// No devices at all: each list refuses with `reason`, as a machine with no
    /// audio and no camera driver does.
    public static CaptureDevices none(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new CaptureDevices(
                () -> {
                    throw new MicrophoneException(reason);
                },
                () -> {
                    throw new CameraException(reason);
                });
    }
}
