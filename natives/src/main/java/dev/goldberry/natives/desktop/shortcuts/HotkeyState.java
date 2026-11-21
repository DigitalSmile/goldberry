package dev.goldberry.natives.desktop.shortcuts;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// Which bound hotkeys are down, so a listener hears each press once and each
/// release once, after its press.
///
/// Every platform needs it for its own reason. An X server repeats a held key
/// as presses, Windows repeats `WM_HOTKEY` unless told not to, and the portal
/// may send `Deactivated` for a shortcut that was rebound while held. So the
/// backends report what they see, and this passes on what a listener wants.
///
/// A listener that throws is logged and does not stop the backend's thread.
final class HotkeyState {

    private static final Logger LOG = Logs.of(HotkeyState.class);

    private final HotkeyListener listener;
    private final Set<String> held = new LinkedHashSet<>();

    HotkeyState(HotkeyListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /// `id` went down. A second press without a release is a repeat, and is
    /// dropped.
    synchronized void pressed(String id) {
        if (held.add(id)) {
            hand(id, true);
        }
    }

    /// `id` came up. A release of a key that is not down is dropped.
    synchronized void released(String id) {
        if (held.remove(id)) {
            hand(id, false);
        }
    }

    /// Whether `id` is down.
    synchronized boolean isHeld(String id) {
        return held.contains(id);
    }

    /// The ids that are down, in the order they went down.
    synchronized List<String> held() {
        return List.copyOf(held);
    }

    /// Releases everything that is down: the hotkeys are being unbound, and a
    /// push-to-talk must not stay open because its release can no longer come.
    synchronized void releaseAll() {
        for (var id : List.copyOf(held)) {
            released(id);
        }
    }

    private void hand(String id, boolean down) {
        try {
            if (down) {
                listener.pressed(id);
            } else {
                listener.released(id);
            }
        } catch (RuntimeException e) {
            LOG.warn("a global shortcut's listener failed on {}", id, e);
        }
    }
}
