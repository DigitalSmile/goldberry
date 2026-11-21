package dev.goldberry.input.global;

import java.util.Objects;

/// What the desktop said to one [GlobalShortcut]: a global shortcut is
/// granted, never assumed.
///
/// ```java
/// switch (grant) {
///     case Grant.Granted(var id, var trigger) -> settings.show(id, trigger);
///     case Grant.Refused(var id, var reason, var detail) -> settings.explain(id, reason);
///     case Grant.Unsupported(var id, var detail) -> settings.hideGlobalShortcuts();
/// }
/// ```
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
public sealed interface Grant {

    /// The shortcut's [GlobalShortcut#id()].
    String id();

    /// Whether events will arrive for it.
    default boolean isGranted() {
        return this instanceof Granted;
    }

    /// Granted: its presses and releases will arrive.
    ///
    /// @param trigger the combination as the desktop describes it. On Wayland
    ///                the user chooses it, so it may not be the one asked for,
    ///                and it is in the desktop's words (`Ctrl+Alt+T`, `Press
    ///                Super+T`); elsewhere it is the one asked for, as the XDG
    ///                shortcuts specification writes it (`CTRL+ALT+t`)
    record Granted(String id, String trigger) implements Grant {
        public Granted {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(trigger, "trigger");
        }
    }

    /// Not granted, though the desktop could have.
    ///
    /// @param detail what the desktop said, for a log line rather than the user
    record Refused(String id, Reason reason, String detail) implements Grant {
        public Refused {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /// There is no way to ask on this desktop: a Wayland desktop whose portal
    /// has no global shortcuts, no display, no system library to bind.
    record Unsupported(String id, String detail) implements Grant {
        public Unsupported {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /// Why a shortcut was refused.
    enum Reason {
        /// The user said no, or closed the dialog that asked.
        DENIED,
        /// Another application holds the combination.
        TAKEN,
        /// The key has no code on this platform or keyboard: `Menu` on a Mac.
        UNMAPPABLE
    }
}
