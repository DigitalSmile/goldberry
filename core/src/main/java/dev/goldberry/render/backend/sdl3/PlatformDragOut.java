package dev.goldberry.render.backend.sdl3;

/// A drag out the platform carries, as the backend drives it once a pump.
sealed interface PlatformDragOut permits X11DragOut, WaylandDragOut {

    /// Reads and acts on what the platform has said, before the pump reads
    /// SDL's events.
    void tick();

    /// Whether the drag is over and what it made is gone.
    boolean isClosed();

    /// Ends the drag now, as a closing backend must.
    void close();

    /// Whether SDL's drop event `type` over `window` belongs to this drag,
    /// which then takes it: a window of the application's own under a drag of
    /// its own is not dropped on by SDL.
    ///
    /// @param x where, in the window's logical pixels
    /// @param y where, in the window's logical pixels
    default boolean takesDrop(int type, Sdl3Window window, float x, float y) {
        return false;
    }
}
