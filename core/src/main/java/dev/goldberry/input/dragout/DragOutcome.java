package dev.goldberry.input.dragout;

import org.jspecify.annotations.Nullable;

/// How a drag out ended, as its source hears it through `onDragOutEnd`.
///
/// ```java
/// card.onDragOutEnd(outcome -> {
///     switch (outcome) {
///         case DragOutcome.Delivered(var operation) when operation == DragOperation.MOVE -> board.remove(frame);
///         case DragOutcome.Delivered _, DragOutcome.Landed _ -> {}
///         case DragOutcome.Cancelled _ -> status.show("Not moved");
///     }
/// })
/// ```
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
public sealed interface DragOutcome {

    /// Another application took the drag.
    ///
    /// @param operation what the target said it did, or null where the platform
    ///                  does not say: some X11 targets, and Wayland compositors
    ///                  older than version 3 of the data device
    record Delivered(@Nullable DragOperation operation) implements DragOutcome {}

    /// The drag was let go over one of this application's own windows, where
    /// it was an in-app drag.
    ///
    /// @param accepted whether a `DropTarget` there took the payload
    record Landed(boolean accepted) implements DragOutcome {}

    /// The drag was put back: Escape, a release over nothing that takes it, or
    /// a target that refused it.
    record Cancelled() implements DragOutcome {}
}
