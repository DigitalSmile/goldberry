package dev.goldberry.example.ui.capture;

import org.jspecify.annotations.Nullable;

/// Closing what a listener registration returned, which says it may throw and
/// never does for the sources here.
final class Subscriptions {

    private Subscriptions() {}

    /// Closes `subscription`, if there is one.
    ///
    /// @throws IllegalStateException when it refuses
    static void close(@Nullable AutoCloseable subscription) {
        if (subscription == null) {
            return;
        }
        try {
            subscription.close();
        } catch (Exception e) {
            throw new IllegalStateException("a subscription refused to close", e);
        }
    }
}
