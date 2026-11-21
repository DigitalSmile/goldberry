package dev.goldberry.natives.sdl.device;

import java.util.Objects;
import java.util.function.Consumer;

import dev.goldberry.natives.sdl.SdlEventWatch;

/// Hears SDL's device events as they are queued: audio devices and cameras
/// coming and going, and the user's answer about a camera.
///
/// SDL queues them only from `SDL_PumpEvents`, which the window loop calls on
/// the main thread, so the listener runs **there, inside the pump**, and only
/// while a loop runs. It must be quick and must not call back into SDL's event
/// queue. Without a window nothing pumps and nothing is heard; a caller that
/// needs the device list then reads it again.
///
/// One [SdlEventWatch] per watch: the existing upcall shape, so the native
/// image registers nothing new.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlDeviceWatch implements AutoCloseable {

    private final SdlEventWatch watch;

    private SdlDeviceWatch(SdlEventWatch watch) {
        this.watch = watch;
    }

    /// Starts telling `listener` about device events. Close to stop.
    public static SdlDeviceWatch install(Consumer<? super SdlDeviceEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        return new SdlDeviceWatch(
                SdlEventWatch.install(event -> SdlDeviceEvent.of(event).ifPresent(listener)));
    }

    /// Stops listening. Idempotent.
    @Override
    public void close() {
        watch.close();
    }
}
