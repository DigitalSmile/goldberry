package dev.goldberry.input.global;

import java.util.Objects;

import dev.goldberry.input.key.Shortcut;

/// One shortcut to ask the desktop for.
///
/// ```java
/// var talk = GlobalShortcut.of("push-to-talk", "Push to talk", "Ctrl+Alt+T");
/// ```
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
///
/// @param id          the application's name for it, which every event
///                    carries and which must be unique within one bind; on
///                    Wayland the desktop also keeps the user's choice of
///                    trigger under it
/// @param description what the desktop shows the user when it asks, and in its
///                    own settings: say what the shortcut does
/// @param trigger     the combination, written as an accelerator is; on
///                    Wayland only a preference, because the user chooses
public record GlobalShortcut(String id, String description, Shortcut trigger) {

    public GlobalShortcut {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(trigger, "trigger");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a global shortcut needs an id");
        }
    }

    /// A shortcut with its trigger parsed from text: `Ctrl+Alt+T`, `F9`.
    ///
    /// @throws IllegalArgumentException if the trigger does not parse, as
    ///         [Shortcut#of(String)] says
    public static GlobalShortcut of(String id, String description, String trigger) {
        return new GlobalShortcut(id, description, Shortcut.of(trigger));
    }
}
