package io.github.digitalsmile.goldberry.bind;

import java.util.Objects;
import java.util.function.Consumer;

/// One woven field, seen as the value a widget can read and watch.
///
/// The counterpart of [Property] for a model that has no properties in it: the
/// cell is the author's own field, and this is the read-only window onto it that
/// [BindingRegistry] hands to the widget tree. A widget cannot tell the two apart, and
/// that is the point — [Observable] is the whole contract, and where the value
/// is actually stored is the model's business
/// ([ADR-0063](../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)).
///
/// Instantiated by woven bytecode, once per path, when a model builds its
/// [BindingRegistry]. Nothing else has any reason to create one.
///
/// @param <T> the value type
public final class BoundField<T> implements Observable<T> {

    private final BoundModel model;
    private final int slot;

    /// A window onto `slot` of `model`.
    ///
    /// Holds the model rather than a copy of the value, because the field is the
    /// cell: a snapshot would be stale the moment the author's own code assigned
    /// to it, which is exactly the assignment the weaver rewired.
    public BoundField(BoundModel model, int slot) {
        this.model = Objects.requireNonNull(model, "model");
        this.slot = slot;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) model.boundValue(slot);
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        return model.boundListeners().subscribe(slot, listener);
    }

    /// How many listeners this field has — the same diagnostic
    /// [Property#listenerCount()] offers, and what a test asserts when it wants
    /// to know a disposed widget really did let go.
    public int listenerCount() {
        return model.boundListeners().listenerCount(slot);
    }

    @Override
    public String toString() {
        return "BoundField[" + get() + ", " + listenerCount() + " listener(s)]";
    }
}
