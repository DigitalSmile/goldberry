package io.github.digitalsmile.goldberry.render.event;

import io.github.digitalsmile.goldberry.render.Backend;

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
    ///
    /// **The rest really do wait**, and it costs a backend something to keep
    /// that promise: by the time a sink throws, the events behind the failing
    /// one have already been taken off the platform's queue, so a backend that
    /// simply unwound would drop them. What that loses is a pointer release that
    /// leaves a button held down, a key release that leaves a modifier stuck, a
    /// `FileDropCompleted` that leaves a drag open — the events whose whole job
    /// is to end something a previous event started, and which nothing can
    /// reconstruct afterwards. So each backend holds the remainder and delivers
    /// it, in order and ahead of anything newer, on the pump after.
    ///
    /// The event that threw is **not** redelivered. This sink saw it and did not
    /// cope, and offering it again would hand the same event to the same handler
    /// on every pump for as long as the caller kept pumping.
    ///
    /// None of which makes throwing a way to decline an event. A caller that
    /// does not stop the loop on an exception here is choosing to carry on with
    /// a handler it knows is broken; what the contract guarantees is only that
    /// the *input* is intact when it does.
    void accept(BackendEvent event);
}
