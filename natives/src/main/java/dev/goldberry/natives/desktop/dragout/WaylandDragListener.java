package dev.goldberry.natives.desktop.dragout;

import org.jspecify.annotations.Nullable;

/// What a Wayland drag source hears from the compositor, on the UI thread,
/// once its queue is dispatched.
///
/// No position: a Wayland source is never told where the pointer is. Over the
/// application's own windows SDL's data device is the target and reports it
/// as SDL's own drop events, which the caller reads beside these. Exactly one
/// of [#finished] and [#cancelled] is heard, once, and nothing after it.
public interface WaylandDragListener {

    /// A target took or refused a type: `mime`, or null for none. A compositor
    /// also says null when the pointer moves to another surface.
    void target(@Nullable String mime);

    /// The button came up over a target that had taken a type. The target now
    /// asks for the data, and [#finished] follows when it has it.
    void dropPerformed();

    /// The target has the data, and did `action` with it, or null where it
    /// did not say.
    void finished(@Nullable DragAction action);

    /// The drag was cancelled, let go over nothing that took it, or replaced.
    void cancelled();
}
