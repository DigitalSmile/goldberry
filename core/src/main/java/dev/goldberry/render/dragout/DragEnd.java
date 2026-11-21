package dev.goldberry.render.dragout;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// How the platform said a drag ended, as a backend reports it.
public sealed interface DragEnd {

    /// Another application's target took it.
    ///
    /// @param operation what the target did, or null where the platform does not say
    record Delivered(@Nullable DragOperation operation) implements DragEnd {}

    /// The button came up over one of this application's own windows.
    ///
    /// @param window the window it was let go over
    /// @param at     where, in that window's logical pixels
    /// @param echoed whether SDL's own drop target took it too, so that a
    ///               `FileDrop` or `TextDrop` of the application's own offer
    ///               is on its way and has to be swallowed
    record ReleasedOver(BackendWindow window, LogicalPoint at, boolean echoed) implements DragEnd {

        public ReleasedOver {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(at, "at");
        }
    }

    /// The drag was cancelled, or let go where nothing took it.
    record Cancelled() implements DragEnd {}
}
