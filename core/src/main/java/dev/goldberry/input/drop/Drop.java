package dev.goldberry.input.drop;

import java.util.Objects;

import dev.goldberry.render.model.LogicalPoint;

/// Something dragged from one widget and let go over another, and where.
///
/// ```java
/// new Column(cards).dropTarget(
///         payload -> payload instanceof Ticket,
///         drop -> board.move((Ticket) drop.payload(), status, drop.at()))
/// ```
///
/// What a [DropTarget] is handed when a drag that started on a `draggable`
/// widget ends over it, and what its `whileOver` hook hears on every move
/// before that.
///
/// ## The payload stays in the process
///
/// It is the very object the source was given by `Attributes.draggable`, not
/// a copy and not a serialised form. This is a drag between two widgets of one
/// application, routed by the window's pointer router; it is not the
/// platform's drag and drop, and nothing outside the application sees it or can
/// end it. A file or a line of text dropped from outside the window is a
/// [FileDrop] or a [TextDrop].
///
/// ## The point is in the target's own content box
///
/// [#at] is measured from the corner of the target's **content box**, inside its
/// padding, which is where `PointerEvent.content()` is measured from too. For a
/// `canvas` that is the corner its painter draws from, so a drop lands in the
/// painter's own coordinates and a week grid can turn it straight into a day
/// and an hour. It is mapped through the same inverse a press is, so a target
/// inside a scrolled `scroll` hears where on itself the drop landed rather than
/// where on the window.
///
/// A drop made from the keyboard has no pointer to measure, and lands at the
/// centre of the target's content box; [#fromKeyboard()] says which it was.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
///
/// @param payload      what the source was picked up with
/// @param at           where on the target it landed, from the corner of its content box
/// @param fromKeyboard whether the drag was carried by the keyboard rather than the pointer
public record Drop(Object payload, LogicalPoint at, boolean fromKeyboard) {

    public Drop {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(at, "at");
    }

    /// A drop made with the pointer.
    public Drop(Object payload, LogicalPoint at) {
        this(payload, at, false);
    }
}
