package dev.goldberry.camera;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;

import dev.goldberry.camera.backend.CameraBackend;
import dev.goldberry.camera.backend.CameraLostException;
import dev.goldberry.camera.backend.DeviceEvent;
import dev.goldberry.camera.backend.SdlCameraBackend;
import dev.goldberry.log.Logs;

/// The cameras there are, and opening them.
///
/// ```java
/// try (var cameras = Cameras.open();
///         var camera = cameras.open(CameraSpec.CALL)) {
///     ...
/// }
/// ```
///
/// [#list()] asks the system each time. Cameras coming and going are told to
/// [#onChange] listeners while the application's window loop runs, on its
/// thread; a program without a window lists again instead. An open `Cameras`
/// keeps the system's cameras listed; closing it does not close the cameras
/// opened through it.
///
/// A camera opens in the uncompressed mode nearest to what was asked
/// ([CameraSpec#choose]); compressed modes are listed and not opened.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class Cameras implements AutoCloseable {

    private static final Logger LOG = Logs.of(Cameras.class);

    private final CameraBackend backend;
    private final AutoCloseable watch;
    private final Map<Integer, CameraInfo> known = new ConcurrentHashMap<>();
    private final Set<DeviceCamera> opened = ConcurrentHashMap.newKeySet();
    private final List<Consumer<? super CameraChange>> listeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    Cameras(CameraBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.watch = backend.watch(this::heard);
    }

    /// The system's cameras, through SDL.
    ///
    /// @throws CameraException when there is no camera driver
    public static Cameras open() {
        try {
            return new Cameras(SdlCameraBackend.open());
        } catch (CameraLostException e) {
            throw new CameraException(String.valueOf(e.getMessage()), e);
        }
    }

    /// The cameras plugged in now, in the system's order.
    public List<CameraInfo> list() {
        ensureOpen();
        var cameras = backend.devices();
        cameras.forEach(camera -> known.put(camera.id(), camera));
        return cameras;
    }

    /// The camera to open when the application has not chosen: the first
    /// facing the user, else the first. Empty with no camera.
    public Optional<CameraInfo> preferred() {
        var cameras = list();
        return cameras.stream()
                .filter(camera -> camera.position() == CameraPosition.FRONT)
                .findFirst()
                .or(() -> cameras.stream().findFirst());
    }

    /// Tells `listener` about cameras plugged in and taken away, on the window
    /// loop's thread, until the subscription is closed.
    ///
    /// @return what removes the listener
    public AutoCloseable onChange(Consumer<? super CameraChange> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Opens the [#preferred()] camera.
    ///
    /// @throws CameraException when there is no camera, or it cannot be opened
    public Camera open(CameraSpec spec) {
        return open(preferred().orElseThrow(() -> new CameraException("there is no camera")), spec);
    }

    /// Opens `camera` in the uncompressed mode nearest to `spec`.
    ///
    /// @throws CameraException when it has no uncompressed mode, cannot be
    ///         opened, or has gone away
    public Camera open(CameraInfo camera, CameraSpec spec) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(spec, "spec");
        ensureOpen();
        var mode = spec.choose(camera.formats())
                .orElseThrow(
                        () -> new CameraException(camera.name() + " offers no uncompressed mode: " + camera.formats()));
        try {
            var stream = backend.open(camera.id(), mode);
            var holder = new DeviceCamera[1];
            holder[0] = new DeviceCamera(camera, mode, stream, () -> opened.remove(holder[0]));
            opened.add(holder[0]);
            known.put(camera.id(), camera);
            return holder[0];
        } catch (CameraLostException e) {
            throw new CameraException("cannot open " + camera.name() + ": " + e.getMessage(), e);
        }
    }

    /// Stops listening for cameras. The cameras opened through this keep
    /// taking pictures until they are closed.
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
        switch (event) {
            case DeviceEvent.Added(var id) -> {
                var camera = backend.devices().stream()
                        .filter(info -> info.id() == id)
                        .findFirst()
                        .orElseGet(() -> unnamed(id));
                known.put(id, camera);
                tell(new CameraChange.Added(camera));
            }
            case DeviceEvent.Removed(var id) -> {
                var camera = Optional.ofNullable(known.remove(id)).orElseGet(() -> unnamed(id));
                for (var open : opened) {
                    if (open.info().id() == id) {
                        open.lose(camera.name() + " was unplugged");
                    }
                }
                tell(new CameraChange.Removed(camera));
            }
            case DeviceEvent.Approved(var id) -> wake(id);
            case DeviceEvent.Denied(var id) -> wake(id);
        }
    }

    private void wake(int id) {
        for (var open : opened) {
            if (open.info().id() == id) {
                open.wake();
            }
        }
    }

    private void tell(CameraChange change) {
        for (var listener : listeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException e) {
                LOG.warn("a camera listener failed", e);
            }
        }
    }

    private static CameraInfo unnamed(int id) {
        return new CameraInfo(id, "", CameraPosition.UNKNOWN, List.of());
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("cameras closed");
        }
    }
}
