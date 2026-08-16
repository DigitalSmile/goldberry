package io.github.digitalsmile.goldberry.css;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/// The themes that ship with the toolkit.
///
/// Each is a stylesheet of custom properties and nothing else (§10), which is why
/// switching one is a single swap in the [CascadeLayer#THEME] slot rather than a
/// restyle of every rule: widget rules read `var(--gb-bg)` and never learn which
/// theme answered.
///
/// Parsed on demand and not cached here. A theme is loaded when a window is built
/// or when the user switches, neither of which is a hot path, and caching a
/// mutable-looking static would fight hot reload (§8) for no measurable gain.
public enum Theme {

    /// Nord light — the default (§10).
    NORD_LIGHT,

    /// Nord dark.
    NORD_DARK;

    /// The theme's resource name, e.g. `nord-light.css`.
    public String resourceName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-') + ".css";
    }

    /// Loads and parses this theme into the theme layer.
    ///
    /// @throws CssSyntaxException if the shipped stylesheet does not parse, which
    ///         is a bug in the toolkit rather than in an application
    /// @throws UncheckedIOException if the resource cannot be read at all
    public Stylesheet load() {
        return Stylesheet.parse(CascadeLayer.THEME, source());
    }

    /// This theme's stylesheet text, as it ships.
    ///
    /// Public because a tool that wants to show a user what a theme *is* — or an
    /// application deriving its own from one — should not have to re-find the
    /// resource.
    public String source() {
        var resource = resourceName();
        try (InputStream in = Theme.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "the " + name() + " theme is missing from the jar: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the " + name() + " theme", e);
        }
    }
}
