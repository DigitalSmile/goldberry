package dev.goldberry.mic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.mic.backend.CaptureBackend;
import dev.goldberry.mic.backend.CaptureLostException;
import dev.goldberry.mic.backend.DeviceEvent;
import dev.goldberry.mic.backend.SdlCaptureBackend;

/// The microphones there are, and recordings from them.
///
/// ```java
/// try (var microphones = Microphones.open();
///         var recording = microphones.record(RecordingSpec.voice())) {
///     recording.onSamples(block -> meter.onSamples(block));
///     ...
/// }
/// ```
///
/// [#list()] asks the system each time. Devices coming and going are told to
/// [#onChange] listeners while the application's window loop runs, on its
/// thread; a program without a window lists again instead. An open
/// `Microphones` keeps the system's audio up; closing it does not end the
/// recordings made through it.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Microphones implements AutoCloseable {

    private static final Logger LOG = Logs.of(Microphones.class);

    /// What the system's choice is called when the system gives it no name.
    static final String SYSTEM_DEFAULT = "System default";

    private final CaptureBackend backend;
    private final AutoCloseable watch;
    private final Map<Integer, Microphone> known = new ConcurrentHashMap<>();
    private final Set<Recording> recordings = ConcurrentHashMap.newKeySet();
    private final List<Consumer<? super MicrophoneChange>> listeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    Microphones(CaptureBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.watch = backend.watch(this::heard);
    }

    /// The system's microphones, through SDL.
    ///
    /// @throws MicrophoneException when there is no audio driver to record with
    public static Microphones open() {
        try {
            return new Microphones(SdlCaptureBackend.open());
        } catch (CaptureLostException e) {
            throw new MicrophoneException(String.valueOf(e.getMessage()), e);
        }
    }

    /// The recording devices plugged in now, in the system's order.
    public List<Microphone> list() {
        ensureOpen();
        return backend.devices().stream()
                .map(device -> {
                    var microphone = new Microphone(device.id(), device.name(), false);
                    known.put(device.id(), microphone);
                    return microphone;
                })
                .toList();
    }

    /// The system's choice: whichever microphone the user has set, followed
    /// when they change it.
    public Microphone systemDefault() {
        ensureOpen();
        var id = backend.systemDefault();
        return new Microphone(
                id, backend.name(id).filter(name -> !name.isBlank()).orElse(SYSTEM_DEFAULT), true);
    }

    /// Tells `listener` about microphones plugged in and taken away, on the
    /// window loop's thread, until the subscription is closed.
    ///
    /// @return what removes the listener
    public AutoCloseable onChange(Consumer<? super MicrophoneChange> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Records the system's choice.
    ///
    /// @throws MicrophoneException when it cannot be opened
    public Recording record(RecordingSpec spec) {
        return record(systemDefault(), spec);
    }

    /// Records `microphone`.
    ///
    /// @throws MicrophoneException when it cannot be opened, or has gone away
    public Recording record(Microphone microphone, RecordingSpec spec) {
        Objects.requireNonNull(microphone, "microphone");
        Objects.requireNonNull(spec, "spec");
        ensureOpen();
        try {
            var stream = backend.open(microphone.id(), spec.format());
            var holder = new Recording[1];
            holder[0] = new Recording(microphone, spec, stream, () -> recordings.remove(holder[0]));
            recordings.add(holder[0]);
            return holder[0];
        } catch (CaptureLostException e) {
            throw new MicrophoneException("cannot record " + microphone.name() + ": " + e.getMessage(), e);
        }
    }

    /// Stops listening for devices and releases the system's audio. The
    /// recordings made through this keep recording until they are closed.
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            watch.close();
        } catch (Exception e) {
            LOG.debug("removing the device watch failed", e);
        }
        backend.close();
    }

    private void heard(DeviceEvent event) {
        MicrophoneChange change = switch (event) {
            case DeviceEvent.Added(var id) -> {
                var microphone = new Microphone(id, backend.name(id).orElse(""), false);
                known.put(id, microphone);
                yield new MicrophoneChange.Added(microphone);
            }
            case DeviceEvent.Removed(var id) -> {
                var microphone = known.remove(id);
                var gone = microphone != null ? microphone : new Microphone(id, "", false);
                for (var recording : recordings) {
                    if (recording.microphone().equals(gone)
                            && !recording.microphone().isSystemDefault()) {
                        recording.lose(gone.name() + " was unplugged");
                    }
                }
                yield new MicrophoneChange.Removed(gone);
            }
        };
        for (var listener : listeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException e) {
                LOG.warn("a microphone listener failed", e);
            }
        }
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("microphones closed");
        }
    }
}
