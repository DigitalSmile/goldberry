package dev.goldberry.render.clipboard;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

import dev.goldberry.bind.Subscription;
import dev.goldberry.log.Logs;

/// The listeners a backend's [Clipboard] tells when its contents change, kept
/// for it.
///
/// One list per clipboard, shared by the backends so that each says only
/// *when* the clipboard changed: SDL on its clipboard event, the headless
/// backend on every write it accepts.
///
/// A listener that throws is logged and the rest are still told, because one
/// broken paste button must not keep the others stale. A listener may close its
/// own subscription, or add another, while it is being told; the list it is
/// told from is the one at the moment of the change.
public final class ClipboardListeners {

    private static final Logger LOG = Logs.of(ClipboardListeners.class);

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    /// An empty list, one per clipboard.
    public ClipboardListeners() {}

    /// Adds `listener`, told on every change until the subscription is closed.
    public Subscription add(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        // A holder of its own, so one listener added twice is two subscriptions
        // and closing one leaves the other.
        var entry = new Runnable() {
            @Override
            public void run() {
                listener.run();
            }
        };
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /// Tells every listener that the clipboard changed.
    public void changed() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.warn("a clipboard listener failed; the others are still told", e);
            }
        }
    }

    /// How many listeners there are now.
    public int size() {
        return listeners.size();
    }
}
