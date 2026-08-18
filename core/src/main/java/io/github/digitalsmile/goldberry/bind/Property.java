package io.github.digitalsmile.goldberry.bind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// One observable value — the whole of §9's "small built-in `Property<T>` type".
///
/// A property is a cell with listeners. It is not a stream, not a computed graph,
/// and has no dependency tracking: those are the things a framework brings, and
/// §9 asks for a binding that needs no framework. What it has is the two
/// operations a binding needs — read the value now, be told when it changes — and
/// nothing else.
///
/// ```java
/// var frost = Property.of(true);
/// bindings.bind("prefs.frost", frost);       // markup can now name it
/// frost.set(false);                          // every bound widget rebuilds
/// ```
///
/// ## Only a change notifies
///
/// `set` compares with [Objects#equals] and does nothing when the value is
/// unchanged. That is what makes two properties mirroring each other terminate
/// rather than recurse: the second `set` finds the value already there and stops.
/// It also means a mutable object set back into a property it is already in
/// notifies nobody — so properties hold values, and a list that is edited in place
/// is not a value.
///
/// ## Who may write
///
/// **The application, and nothing in the widget tree.** A widget is handed the
/// [Observable] half of this — the same value with no `set` on it — so a control
/// built from markup cannot write to the model, and data flows down while events
/// flow back up
/// ([ADR-0063](../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)).
/// Keep the `Property` where the state belongs; hand out the path.
///
/// ## Threads
///
/// Confined to the UI thread, like everything a listener will touch: a listener
/// rebuilds a widget, and the widget tree belongs to that thread
/// ([ADR-0020](../../../../../../book/src/adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)).
/// Background work reaches a property the same way it reaches anything else, by
/// completing on the UI thread.
///
/// @param <T> the value type
public final class Property<T> implements Observable<T> {

    private final List<Consumer<? super T>> listeners = new ArrayList<>();

    private T value;

    private Property(T initial) {
        this.value = initial;
    }

    /// A property holding `initial`.
    ///
    /// Null is allowed: "no value yet" is a state a binding has to be able to
    /// represent, and a property that refused it would push every application
    /// into an `Optional` it did not ask for.
    public static <T> Property<T> of(T initial) {
        return new Property<>(initial);
    }

    /// The current value.
    @Override
    public T get() {
        return value;
    }

    /// Sets the value and notifies, unless it is the value already there.
    ///
    /// Listeners are notified over a snapshot of the list, so one that
    /// unsubscribes — or subscribes — while being called does not disturb the
    /// notification in progress. A listener that sets this property again is
    /// notified in turn, and terminates because the second set finds nothing to
    /// change.
    ///
    /// @return whether anything changed
    public boolean set(T next) {
        if (Objects.equals(value, next)) {
            return false;
        }
        value = next;
        for (var listener : List.copyOf(listeners)) {
            listener.accept(next);
        }
        return true;
    }

    /// Registers `listener`, which is called on every change until the returned
    /// subscription is closed.
    ///
    /// **Not called with the current value.** A subscriber reads [#get()] for
    /// that, at the moment it is ready to — firing on subscribe would mean a
    /// widget rebuilding itself while it is being built.
    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return new Subscription() {

            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    listeners.remove(listener);
                }
            }
        };
    }

    /// How many listeners are registered.
    ///
    /// Diagnostics, and the assertion a test makes when it wants to know that a
    /// disposed widget really did let go — a subscription that outlives its widget
    /// keeps the whole subtree alive and rebuilds something nobody can see.
    public int listenerCount() {
        return listeners.size();
    }

    @Override
    public String toString() {
        return "Property[" + value + ", " + listeners.size() + " listener(s)]";
    }
}
