package dev.goldberry.render.backend.sdl3;

/// How long a drag out takes SDL's drop events for itself, where SDL's own
/// drop target hears the application's own drag (Windows, macOS): for as long
/// as the drag lasts, and through the pump after its end, which reads the drop
/// SDL's target queued for the application's own offer.
///
/// The drag says when it [#ended]; the backend [#tick]s it once a pump.
final class DropEchoes {

    private boolean ended;
    private boolean drained;
    private boolean closed;

    /// The platform has said how the drag ended.
    void ended() {
        ended = true;
    }

    /// Whether the platform has said how the drag ended.
    boolean isEnded() {
        return ended;
    }

    /// One pump: the first after the ending still takes what SDL queued,
    /// the second closes.
    void tick() {
        if (!ended || closed) {
            return;
        }
        if (drained) {
            closed = true;
        } else {
            drained = true;
        }
    }

    /// Ends it now.
    void close() {
        closed = true;
    }

    /// Whether the drag and its echoes are over.
    boolean isClosed() {
        return closed;
    }
}
