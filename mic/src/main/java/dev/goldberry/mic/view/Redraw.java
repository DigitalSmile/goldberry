package dev.goldberry.mic.view;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.goldberry.Goldberry;

/// How a widget that hears a source on the source's thread draws again on the
/// UI thread: once, however many blocks arrive before the UI thread gets to it.
final class Redraw {

    /// The UI thread, asked for when a redraw is first wanted.
    static final Executor UI = task -> Goldberry.ui().execute(task);

    private Redraw() {}

    /// What a source's listener runs after each block: hands `refresh` to `ui`
    /// once, and not again until it has run.
    static Runnable coalesced(AtomicBoolean pending, Executor ui, Runnable refresh) {
        return () -> {
            if (pending.compareAndSet(false, true)) {
                ui.execute(() -> {
                    pending.set(false);
                    refresh.run();
                });
            }
        };
    }
}
