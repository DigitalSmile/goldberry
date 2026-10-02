package dev.goldberry;

import dev.goldberry.render.Backend;

/// Reaches the package-private runtime from a test in another package.
///
/// `GoldberryRuntime.install` is deliberately not public — an application
/// choosing its own backend is a real use case but not one with a caller yet,
/// and a setter that must run before an implicit initialization is a bad shape
/// to publish. Tests outside this package still need it, and a
/// test-only door is better than widening the real one.
///
/// Read more: [A host for a test](https://goldberry.dev/docs/guide/testing.html#a-host-for-a-test).
public final class GoldberryTestAccess {

    private GoldberryTestAccess() {}

    /// Installs `backend` as the runtime's, before anything starts one.
    public static void install(Backend backend) {
        GoldberryRuntime.install(backend);
    }

    /// Takes it down again, so the next test starts from nothing.
    public static void shutdown() {
        GoldberryRuntime.shutdown();
    }

    /// An overlay on `widget` covering the whole window, attached as a window's
    /// overlay layer attaches one: [Overlay#remove()] runs `onRemove` once.
    ///
    /// For a test host's [Host#fill]. A detached overlay's `remove()` does
    /// nothing, so a host returning one could not tell whether a widget took
    /// its overlay away again.
    public static Overlay attachedFilling(dev.goldberry.widget.Widget widget, Runnable onRemove) {
        var overlay = Overlay.filling(widget);
        overlay.attached(onRemove);
        return overlay;
    }
}
