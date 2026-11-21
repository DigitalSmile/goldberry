package dev.goldberry.natives.wayland;

/// What hears one proxy's events, on the thread that dispatches its queue.
@FunctionalInterface
public interface WaylandListener {

    /// `event` arrived. It is valid for this call only.
    void onEvent(WaylandEvent event);
}
