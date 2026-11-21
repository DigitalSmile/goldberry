package dev.goldberry.mic.backend;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.audio.SdlAudioDevices;
import dev.goldberry.natives.sdl.audio.SdlRecordingStream;
import dev.goldberry.natives.sdl.device.SdlDeviceEvent;
import dev.goldberry.natives.sdl.device.SdlDeviceWatch;

/// The desktop's backend: SDL's recording devices and streams, and its device
/// events, which reach a watch only while the window loop pumps.
public final class SdlCaptureBackend implements CaptureBackend {

    private final SdlAudioDevices devices;

    private SdlCaptureBackend(SdlAudioDevices devices) {
        this.devices = devices;
    }

    /// Starts SDL's audio subsystem, or takes another reference on it.
    ///
    /// @throws CaptureLostException when SDL has no audio driver to start
    public static SdlCaptureBackend open() {
        try {
            return new SdlCaptureBackend(SdlAudioDevices.open());
        } catch (SdlException e) {
            throw new CaptureLostException("SDL has no audio driver: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CaptureDevice> devices() {
        return devices.recordingDevices().stream()
                .map(device -> new CaptureDevice(device.id(), device.name()))
                .toList();
    }

    @Override
    public int systemDefault() {
        return SdlAudioDevices.defaultRecording();
    }

    @Override
    public Optional<String> name(int id) {
        return devices.name(id);
    }

    @Override
    public CaptureStream open(int id, SampleFormat format) {
        try {
            return new Stream(SdlRecordingStream.open(id, format.sampleRate(), format.channels()));
        } catch (SdlException e) {
            throw new CaptureLostException("SDL could not open the device: " + e.getMessage(), e);
        }
    }

    @Override
    public AutoCloseable watch(Consumer<DeviceEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        var watch = SdlDeviceWatch.install(event -> {
            switch (event) {
                case SdlDeviceEvent.AudioAdded(var device, var recording)
                when recording -> listener.accept(new DeviceEvent.Added(device));
                case SdlDeviceEvent.AudioRemoved(var device, var recording)
                when recording -> listener.accept(new DeviceEvent.Removed(device));
                default -> {
                    // Playback devices, format changes and cameras are not ours.
                }
            }
        });
        return watch;
    }

    @Override
    public void close() {
        devices.close();
    }

    /// SDL's stream, its failures read as a lost device.
    private record Stream(SdlRecordingStream stream) implements CaptureStream {

        @Override
        public int read(ByteBuffer into) {
            try {
                return stream.read(into);
            } catch (SdlException e) {
                throw new CaptureLostException("the device stopped recording: " + e.getMessage(), e);
            }
        }

        @Override
        public void pause() {
            stream.pause();
        }

        @Override
        public void resume() {
            stream.resume();
        }

        @Override
        public void close() {
            stream.close();
        }
    }
}
