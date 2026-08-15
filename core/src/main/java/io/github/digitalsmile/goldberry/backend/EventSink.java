package io.github.digitalsmile.goldberry.backend;

/// Where a backend delivers the events it translated.
///
/// Called on the UI thread, from inside [Backend#pumpEvents], one event at a
/// time and in the order the platform reported them. Order matters: a resize
/// followed by a frame is a different frame from a frame followed by a resize.
@FunctionalInterface
public interface EventSink {

    /// Handles one event.
    ///
    /// Throwing propagates out of `pumpEvents` — the backend is mid-drain and has
    /// no way to make sense of a failure here, so it does not try. Events already
    /// delivered stay delivered; the rest wait for the next pump.
    void accept(BackendEvent event);
}
