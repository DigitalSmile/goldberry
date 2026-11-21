package dev.goldberry.mic.backend;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import dev.goldberry.mic.source.SampleFormat;

/// What `Microphones` records through: SDL on the desktop, a fake in a test.
///
/// Not exported. An open backend keeps its device ids valid; closing it does
/// not close the streams it opened, which keep their device to themselves.
public interface CaptureBackend extends AutoCloseable {

    /// The recording devices there are now.
    List<CaptureDevice> devices();

    /// The id that records from whatever device the system has chosen, and
    /// follows it when that changes.
    int systemDefault();

    /// The name of device `id`, or empty for one the backend does not know.
    Optional<String> name(int id);

    /// Opens a stream on device `id` in `format`, paused.
    ///
    /// @throws CaptureLostException when the device cannot be opened
    CaptureStream open(int id, SampleFormat format);

    /// Tells `listener` about devices coming and going, on whatever thread the
    /// backend hears it, until the subscription is closed.
    AutoCloseable watch(Consumer<DeviceEvent> listener);

    @Override
    void close();
}
