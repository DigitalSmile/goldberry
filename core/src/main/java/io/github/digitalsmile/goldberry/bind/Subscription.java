package io.github.digitalsmile.goldberry.bind;

/// A registration that can be undone.
///
/// [AutoCloseable] without the checked exception, so it works in
/// try-with-resources and as a field a `dispose()` calls. Closing twice is a
/// no-op — a state that unsubscribes in `dispose()` and a caller that unsubscribes
/// itself must not fight.
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    /// Cancels the registration. Idempotent.
    @Override
    void close();
}
