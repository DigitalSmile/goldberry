package dev.goldberry.input.drop;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/// What a widget does with something dragged onto it: which payloads it takes,
/// what it does with one let go over it, and, if it wants to draw where the drop
/// would land, what it hears while one is held over it.
///
/// ```java
/// new Canvas(week::paint, week).dropTarget(DropTarget.of(
///                 payload -> payload instanceof Event,
///                 drop -> week.move((Event) drop.payload(), drop.at()))
///         .whileOver(drop -> week.showSlotAt(drop.at()))
///         .onLeave(week::hideSlot))
/// ```
///
/// Carried by `Attributes.dropTarget`, so any widget can be one. The pointer
/// router asks [#accepts] whenever it looks for a target under the pointer,
/// which is on every move, so it should be a cheap question. A target that says
/// no is passed over: it gets no `:drag-over`, hears nothing, and the drag goes
/// on looking up the tree for an ancestor that says yes.
///
/// ## The hooks
///
/// - [#onDrop] runs once, when a drag is let go over a target that accepts it.
/// - [#whileOver] runs on every move while an accepted drag is over the target,
///   with the payload and the point in the target's content box. It is what a
///   `canvas` uses to draw a drop indicator, since it hears no pointer events
///   while somebody else holds the pointer. Null hears nothing.
/// - [#onLeave] runs when an accepted drag stops being over the target, by
///   moving off, by being dropped (just before [#onDrop]), or by being
///   cancelled. Every [#whileOver] run is followed by one. Null hears nothing.
///
/// A stylesheet needs none of them: the target matches `:drag-over` while an
/// accepted drag is over it.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
///
/// @param accepts   whether a payload may be dropped here
/// @param onDrop    what to do with one that was
/// @param whileOver told where an accepted drag is on every move, or null
/// @param onLeave   told when an accepted drag is no longer over this, or null
public record DropTarget(
        Predicate<Object> accepts,
        Consumer<Drop> onDrop,
        @Nullable Consumer<Drop> whileOver,
        @Nullable Runnable onLeave) {

    public DropTarget {
        Objects.requireNonNull(accepts, "accepts");
        Objects.requireNonNull(onDrop, "onDrop");
    }

    /// A target that takes what `accepts` says yes to, and does `onDrop` with it.
    public static DropTarget of(Predicate<Object> accepts, Consumer<Drop> onDrop) {
        return new DropTarget(accepts, onDrop, null, null);
    }

    /// This target, also told where an accepted drag is on every move.
    public DropTarget whileOver(@Nullable Consumer<Drop> value) {
        return new DropTarget(accepts, onDrop, value, onLeave);
    }

    /// This target, also told when an accepted drag stops being over it.
    public DropTarget onLeave(@Nullable Runnable value) {
        return new DropTarget(accepts, onDrop, whileOver, value);
    }
}
