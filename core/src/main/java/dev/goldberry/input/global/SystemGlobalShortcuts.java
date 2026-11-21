package dev.goldberry.input.global;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.natives.desktop.shortcuts.Hotkey;
import dev.goldberry.natives.desktop.shortcuts.HotkeyBackend;
import dev.goldberry.natives.desktop.shortcuts.HotkeyKey;
import dev.goldberry.natives.desktop.shortcuts.HotkeyListener;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome;
import dev.goldberry.render.desktop.notify.PlatformNotifier;

/// [GlobalShortcuts#open()]: the platform's [HotkeyBackend], with the
/// toolkit's words on what crosses and its events moved to an executor.
final class SystemGlobalShortcuts implements GlobalShortcuts {

    private final @Nullable HotkeyBackend backend;
    private final Executor executor;
    private boolean closed;

    SystemGlobalShortcuts(@Nullable HotkeyBackend backend, Executor executor) {
        this.backend = backend;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    static SystemGlobalShortcuts open(Executor executor) {
        return new SystemGlobalShortcuts(
                HotkeyBackend.forThisPlatform(PlatformNotifier.desktopId()).orElse(null), executor);
    }

    /// The UI thread, when the caller is on it; otherwise the thread each
    /// event arrives on, so an event is never queued where no loop will run
    /// it.
    static Executor uiThread() {
        return Goldberry.isUiThread() ? Goldberry.ui() : Runnable::run;
    }

    @Override
    public String name() {
        return backend == null ? "none" : backend.name();
    }

    @Override
    public boolean isSupported() {
        return backend != null;
    }

    @Override
    public synchronized CompletableFuture<List<Grant>> bind(
            List<GlobalShortcut> shortcuts, Consumer<GlobalShortcutEvent> listener) {
        var asked = List.copyOf(shortcuts);
        Objects.requireNonNull(listener, "listener");
        requireDistinctIds(asked);
        if (closed) {
            throw new IllegalStateException("these global shortcuts are closed");
        }
        var platform = backend;
        if (platform == null) {
            return CompletableFuture.supplyAsync(
                    () -> asked.stream()
                            .<Grant>map(shortcut -> new Grant.Unsupported(
                                    shortcut.id(), "this desktop has no global shortcuts this build can reach"))
                            .toList(),
                    executor);
        }
        var hotkeys = asked.stream().map(SystemGlobalShortcuts::hotkey).toList();
        var heard = listening(listener);
        var outcomes = platform.bindsOnMainThread()
                ? CompletableFuture.completedFuture(hotkeys)
                        .thenComposeAsync(asking -> platform.bind(asking, heard), executor)
                : platform.bind(hotkeys, heard);
        return outcomes.thenApplyAsync(
                list -> list.stream().map(SystemGlobalShortcuts::grant).toList(), executor);
    }

    /// The listener the backend calls on its own thread, moving each event to
    /// the executor.
    private HotkeyListener listening(Consumer<GlobalShortcutEvent> listener) {
        return new HotkeyListener() {
            @Override
            public void pressed(String id) {
                executor.execute(() -> listener.accept(new GlobalShortcutEvent(id, GlobalShortcutEvent.Kind.PRESSED)));
            }

            @Override
            public void released(String id) {
                executor.execute(() -> listener.accept(new GlobalShortcutEvent(id, GlobalShortcutEvent.Kind.RELEASED)));
            }
        };
    }

    static void requireDistinctIds(List<GlobalShortcut> shortcuts) {
        var ids = new HashSet<String>();
        for (var shortcut : shortcuts) {
            if (!ids.add(shortcut.id())) {
                throw new IllegalArgumentException("two global shortcuts are called " + shortcut.id());
            }
        }
    }

    /// The native layer's hotkey for a shortcut. `Key`'s constants and
    /// `HotkeyKey`'s are named alike, and `Mod`'s bits are `Hotkey`'s, which
    /// a test holds together.
    static Hotkey hotkey(GlobalShortcut shortcut) {
        return new Hotkey(
                shortcut.id(),
                shortcut.description(),
                key(shortcut.trigger()),
                shortcut.trigger().modifiers().mask());
    }

    static HotkeyKey key(Shortcut trigger) {
        return HotkeyKey.valueOf(trigger.key().name());
    }

    static Grant grant(HotkeyOutcome outcome) {
        return switch (outcome) {
            case HotkeyOutcome.Bound(var id, var trigger) -> new Grant.Granted(id, trigger);
            case HotkeyOutcome.Refused(var id, var reason, var detail) ->
                switch (reason) {
                    case DENIED -> new Grant.Refused(id, Grant.Reason.DENIED, detail);
                    case TAKEN -> new Grant.Refused(id, Grant.Reason.TAKEN, detail);
                    case UNMAPPABLE -> new Grant.Refused(id, Grant.Reason.UNMAPPABLE, detail);
                    case UNSUPPORTED -> new Grant.Unsupported(id, detail);
                };
        };
    }

    @Override
    public void close() {
        HotkeyBackend platform;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            platform = backend;
        }
        if (platform == null) {
            return;
        }
        if (platform.bindsOnMainThread()) {
            executor.execute(platform::close);
        } else {
            platform.close();
        }
    }
}
