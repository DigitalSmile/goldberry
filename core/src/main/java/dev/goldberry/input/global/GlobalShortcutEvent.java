package dev.goldberry.input.global;

import java.util.Objects;

/// A granted shortcut going down or coming up.
///
/// One press, then one release: a held key's repeats are not passed on, and
/// a release never comes without its press. When the shortcuts are unbound or
/// closed while one is held, its release comes then, so a push-to-talk never
/// stays open.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
///
/// @param id   the shortcut's [GlobalShortcut#id()]
/// @param kind down or up
public record GlobalShortcutEvent(String id, Kind kind) {

    /// Down or up.
    public enum Kind {
        PRESSED,
        RELEASED
    }

    public GlobalShortcutEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }

    /// Whether this is the shortcut going down.
    public boolean isPress() {
        return kind == Kind.PRESSED;
    }
}
