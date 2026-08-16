package io.github.digitalsmile.goldberry.bind;

import java.util.function.Consumer;

/// A value that can be read and watched, and **not** written.
///
/// This is what a widget gets. [Property] is the same value with `set` on it, and
/// it is what the application keeps: data flows down into the tree and events
/// flow back up, so a control reports what the user did and the application
/// decides what that means
/// ([ADR-0063](../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)).
///
/// The split is types rather than discipline. `Bindings.resolve` hands back one of
/// these, so a widget built from markup **cannot** write to the model even by
/// accident — there is no method to call. A markup document that could mutate an
/// application's state would be code in a data file, and it would be code with no
/// stack trace: the write would come from a `bind=` attribute somebody edited
/// while the window was open.
///
/// Confined to the UI thread, like the property behind it.
///
/// @param <T> the value type
public interface Observable<T> {

    /// The current value.
    T get();

    /// Registers `listener`, which is called on every change until the returned
    /// subscription is closed.
    ///
    /// **Not called with the current value.** A subscriber reads [#get()] for
    /// that, at the moment it is ready to — firing on subscribe would mean a
    /// widget rebuilding itself while it is being built.
    Subscription subscribe(Consumer<? super T> listener);
}
