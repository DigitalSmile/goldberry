package dev.goldberry.input.global;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/// Global shortcuts of a test's own: it grants what it is asked for, except
/// the shortcuts the test says it refuses, and presses and releases what the
/// test tells it to, on the test's thread.
///
/// ```java
/// var shortcuts = GlobalShortcuts.simulated();
/// var app = new Call(shortcuts);          // binds "talk"
/// shortcuts.press("talk");
/// assertTrue(app.microphoneOpen());
/// shortcuts.release("talk");
/// ```
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
public final class SimulatedGlobalShortcuts implements GlobalShortcuts {

    private final Map<String, Grant.Reason> refusals = new HashMap<>();
    private final Set<String> granted = new LinkedHashSet<>();
    private final Set<String> held = new LinkedHashSet<>();
    private List<GlobalShortcut> bound = List.of();
    private @Nullable Consumer<GlobalShortcutEvent> listener;
    private boolean closed;

    SimulatedGlobalShortcuts() {}

    /// From now on, refuses `id` for `reason`, as a desktop would.
    public synchronized SimulatedGlobalShortcuts refuse(String id, Grant.Reason reason) {
        refusals.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(reason, "reason"));
        return this;
    }

    /// The user presses `id`. Nothing happens for a shortcut that is not
    /// granted, or that is already down.
    public void press(String id) {
        deliver(id, GlobalShortcutEvent.Kind.PRESSED);
    }

    /// The user lets go of `id`. Nothing happens for one that is not down.
    public void release(String id) {
        deliver(id, GlobalShortcutEvent.Kind.RELEASED);
    }

    /// The shortcuts bound now, granted or not.
    public synchronized List<GlobalShortcut> bound() {
        return bound;
    }

    /// Whether these are closed.
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public CompletableFuture<List<Grant>> bind(List<GlobalShortcut> shortcuts, Consumer<GlobalShortcutEvent> listener) {
        var asked = List.copyOf(shortcuts);
        Objects.requireNonNull(listener, "listener");
        SystemGlobalShortcuts.requireDistinctIds(asked);
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("these global shortcuts are closed");
            }
        }
        releaseAll();
        synchronized (this) {
            this.listener = listener;
            bound = asked;
            granted.clear();
            var grants = asked.stream()
                    .<Grant>map(shortcut -> {
                        var reason = refusals.get(shortcut.id());
                        if (reason != null) {
                            return new Grant.Refused(shortcut.id(), reason, "simulated");
                        }
                        granted.add(shortcut.id());
                        return new Grant.Granted(
                                shortcut.id(), shortcut.trigger().toString());
                    })
                    .toList();
            return CompletableFuture.completedFuture(grants);
        }
    }

    private void deliver(String id, GlobalShortcutEvent.Kind kind) {
        Consumer<GlobalShortcutEvent> target;
        synchronized (this) {
            target = listener;
            if (target == null || !granted.contains(id)) {
                return;
            }
            var changed = kind == GlobalShortcutEvent.Kind.PRESSED ? held.add(id) : held.remove(id);
            if (!changed) {
                return;
            }
        }
        target.accept(new GlobalShortcutEvent(id, kind));
    }

    private void releaseAll() {
        List<String> down;
        synchronized (this) {
            down = List.copyOf(held);
        }
        down.forEach(this::release);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
        }
        releaseAll();
        synchronized (this) {
            closed = true;
            granted.clear();
            bound = List.of();
            listener = null;
        }
    }
}
