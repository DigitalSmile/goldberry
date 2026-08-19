package io.github.digitalsmile.goldberry.bind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The listeners of one woven model, one slot per `@Bind` field.
///
/// A model woven by the toolkit ([Model]) keeps exactly one of these, and every
/// rewritten field write ends in a [#fire] on the slot that field was given. It
/// is the whole of the "raw field behaves like a [Property]" trick: the cell is
/// the field itself — already there, already the right type, already the thing
/// the author's own code reads and writes — and this is only the part a plain
/// field cannot have, which is somebody to tell.
///
/// ## Why an index and not a name
///
/// The weaver assigns each `@Bind` field a slot at build time and writes that
/// integer into the bytecode. So a notification is an array index rather than a
/// map lookup on a string, and the field's name survives only in the binding
/// path — which is the one place a person actually reads it.
///
/// ## Slots are lazy
///
/// Most fields of most models are never subscribed to: a value shown by no
/// widget still gets woven, because whether a document binds it is not something
/// the build can know. An unsubscribed slot is a null and costs one array entry.
///
/// Confined to the UI thread, like [Property] and for the same reason — a
/// listener rebuilds a widget, and the widget tree belongs to that thread.
public final class FieldListeners {

    /// One list per woven field, allocated when something first subscribes.
    private final List<Consumer<Object>>[] slots;

    /// One [BoundField] per woven field, created the first time a path is
    /// resolved and kept.
    ///
    /// Kept rather than created per call because the identity of an observable is
    /// something the toolkit compares: a widget's `binding()` is matched against
    /// what a registry resolved, and two windows onto one field that are not the
    /// same object would make that comparison false for a value that has not
    /// moved. It also means rebuilding a [BindingRegistry] — which happens on every
    /// document reload — allocates nothing.
    private final Observable<?>[] views;

    /// Listeners waiting to be told a frame is wanted.
    ///
    /// Called from the setter of a field the weaver saw declared `@Bind` without
    /// `repaint = false`. **Which fields ask is decided in the build**, so a value
    /// nothing displays costs not a branch here but an instruction that is not
    /// there (ADR-0135).
    private List<Runnable> repaint;

    /// Listeners for a change to a field declared `@Bind(restyle = true)`.
    ///
    /// Separate from [#repaint] because a restyle is much more expensive than
    /// a repaint — every resolved style is thrown away — and the common case is a
    /// change that moves no rule at all (ADR-0133).
    private List<Runnable> restyle;

    /// A store for a model with `fields` woven `@Bind` fields.
    ///
    /// Called from woven constructors, which pass the count the weaver counted.
    @SuppressWarnings("unchecked")
    public FieldListeners(int fields) {
        if (fields < 0) {
            throw new IllegalArgumentException("fields must not be negative, was " + fields);
        }
        // A wildcard array is reifiable, so this is the one array creation that
        // needs a cast rather than a raw type.
        this.slots = (List<Consumer<Object>>[]) new List<?>[fields];
        this.views = new Observable<?>[fields];
    }

    /// The read-only window onto one field, created once and kept.
    ///
    /// Called from the woven `bindings()`, which is why it takes the model back:
    /// this store belongs to the model but does not hold it, so a listener list
    /// that outlives its model keeps nothing alive.
    public Observable<?> view(BoundModel model, int field) {
        var existing = views[field];
        if (existing != null) {
            return existing;
        }
        var created = new BoundField<>(model, field);
        views[field] = created;
        return created;
    }

    /// Registers `listener`, called after a field that asks for a frame changes.
    ///
    /// **After** the per-field listeners for that change, so a subscriber
    /// watching a particular path has already run by the time the frame is asked
    /// for.
    ///
    /// Fired once per *change*, not once per write: a write that assigns the
    /// value already there notifies nobody, here as everywhere.
    public Subscription onRepaint(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (repaint == null) {
            repaint = new ArrayList<>(2);
        }
        repaint.add(listener);
        return new Subscription() {

            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    repaint.remove(listener);
                }
            }
        };
    }

    /// Registers `listener`, called after a `@Bind(restyle = true)` field
    /// changes.
    public Subscription onRestyle(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (restyle == null) {
            restyle = new ArrayList<>(1);
        }
        restyle.add(listener);
        return new Subscription() {

            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    restyle.remove(listener);
                }
            }
        };
    }

    /// Notifies the restyle listeners.
    ///
    /// Called from the setter of a field the weaver saw declared
    /// `@Bind(restyle = true)`, before [#fire] tells anybody else — so a window
    /// has dropped its resolved styles by the time the frame it is about to be
    /// asked for is built.
    public void restyled() {
        if (restyle == null || restyle.isEmpty()) {
            return;
        }
        for (var listener : List.copyOf(restyle)) {
            listener.run();
        }
    }

    /// How many fields this store has slots for — the weaver's count, which a
    /// test asserts against the model's annotations.
    public int size() {
        return slots.length;
    }

    /// Registers `listener` on one field, until the returned subscription is
    /// closed.
    ///
    /// **Not called with the current value**, exactly like
    /// [Property#subscribe]: a subscriber reads the value when it is ready to,
    /// and firing on subscribe would mean a widget rebuilding itself while it is
    /// being built.
    @SuppressWarnings("unchecked")
    public Subscription subscribe(int field, Consumer<?> listener) {
        Objects.requireNonNull(listener, "listener");
        var typed = (Consumer<Object>) listener;
        var list = slots[field];
        if (list == null) {
            list = new ArrayList<>(2);
            slots[field] = list;
        }
        list.add(typed);
        var target = list;
        return new Subscription() {

            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    target.remove(typed);
                }
            }
        };
    }

    /// Notifies one field's listeners.
    ///
    /// Called from the setter the weaver synthesised, **after** the field has
    /// been written and only when the value actually changed — the comparison
    /// happens in the generated setter, where the field's real type is known and
    /// an `int` can be compared as an `int` rather than boxed to be asked.
    ///
    /// Listeners are notified over a snapshot, so one that unsubscribes — or
    /// subscribes — while being called does not disturb the notification in
    /// progress. This is [Property#set]'s rule, and it has to be the same one:
    /// the two are the same binding seen from two sides.
    public void fire(int field, Object value) {
        var list = slots[field];
        if (list == null || list.isEmpty()) {
            return;
        }
        for (var listener : List.copyOf(list)) {
            listener.accept(value);
        }
    }

    /// Asks for a frame.
    ///
    /// Called from the setter of a field that was not declared
    /// `@Bind(repaint = false)`, after [#fire] — so whatever a widget does with
    /// the new value has already happened when the window is asked to draw it.
    public void repainted() {
        if (repaint == null || repaint.isEmpty()) {
            return;
        }
        for (var listener : List.copyOf(repaint)) {
            listener.run();
        }
    }

    /// How many listeners one field has.
    ///
    /// Diagnostics, and the assertion a test makes when it wants to know that a
    /// disposed widget really did let go — a subscription that outlives its
    /// widget keeps the whole subtree alive and rebuilds something nobody can
    /// see.
    public int listenerCount(int field) {
        var list = slots[field];
        return list == null ? 0 : list.size();
    }
}
