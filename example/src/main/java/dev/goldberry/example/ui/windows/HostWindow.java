package dev.goldberry.example.ui.windows;

import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.Window;

/// The window under a host, for the cards that need the escape hatch.
///
/// A host with no window under it, an offscreen picture or a test, throws from
/// `window()`. A card asks here instead and shows that answer rather than
/// failing.
final class HostWindow {

    private HostWindow() {}

    /// The window, or empty where `host` has none.
    static Optional<Window> of(Host host) {
        try {
            return Optional.of(host.window());
        } catch (UnsupportedOperationException noWindow) {
            return Optional.empty();
        }
    }

    /// What a card says when there is no window to ask.
    static final String NONE = "This host has no window under it, so there is nothing to ask.";
}
