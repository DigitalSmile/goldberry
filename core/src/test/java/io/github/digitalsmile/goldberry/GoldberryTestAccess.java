package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.Backend;

/// Reaches the package-private runtime from a test in another package.
///
/// `GoldberryRuntime.install` is deliberately not public — an application
/// choosing its own backend is a real use case but not one with a caller yet,
/// and a setter that must run before an implicit initialization is a bad shape
/// to publish (ADR-0019). Tests outside this package still need it, and a
/// test-only door is better than widening the real one.
public final class GoldberryTestAccess {

    private GoldberryTestAccess() {
    }

    /// Installs `backend` as the runtime's, before anything starts one.
    public static void install(Backend backend) {
        GoldberryRuntime.install(backend);
    }
}
