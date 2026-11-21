package dev.goldberry.natives.desktop.shortcuts;

import java.util.Objects;

/// What the desktop said to one [Hotkey].
public sealed interface HotkeyOutcome {

    /// The hotkey's id.
    String id();

    /// Granted: presses and releases will arrive.
    ///
    /// @param trigger the trigger as the desktop describes it, which the portal
    ///                lets the user change; elsewhere the one asked for
    record Bound(String id, String trigger) implements HotkeyOutcome {
        public Bound {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(trigger, "trigger");
        }
    }

    /// Not granted, and why.
    record Refused(String id, Refusal reason, String detail) implements HotkeyOutcome {
        public Refused {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /// Why a hotkey was not granted.
    enum Refusal {
        /// The user said no, or closed the dialog that asked.
        DENIED,
        /// Another application holds the combination.
        TAKEN,
        /// The key has no code on this platform or this keyboard.
        UNMAPPABLE,
        /// There is no way to ask on this desktop: no portal, no display, no
        /// library.
        UNSUPPORTED
    }
}
