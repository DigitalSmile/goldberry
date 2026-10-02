package dev.goldberry.widgets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.parse.ParseMode;

/// How tall a control is: the density preference, applied to the whole
/// application.
///
/// ```java
/// var sheets = Controls.stylesheets(theme, Density.COMPACT);
/// ```
///
/// An application that sizes its controls from the tokens adapts with no code
/// of its own: [Controls#baseStylesheet()] sizes every control from
/// `--gb-control-height` rather than from a literal, so switching a density is a
/// stylesheet swap in the [CascadeLayer#THEME] slot and no widget learns which
/// one answered — the same mechanism, and the same slot, as switching a theme
/// ([dev.goldberry.css.Theme]).
///
/// It lives in `:widgets` rather than beside `Theme` in `:core` because a
/// density sizes *controls*, and `:core`'s primitives have no height for one to
/// move. A theme is in `:core` for the opposite reason: `row` and `text` read
/// `--gb-bg` and `--gb-text` too.
///
/// [#REGULAR] ships no stylesheet. Regular is not something an application
/// applies; it is what the toolkit already is — the numbers are in
/// `controls.css` with every other metric — so [#stylesheets()] is empty for
/// it, and there is no `density-regular.css` restating 32 in a second file for
/// the two to drift apart in. A default is the absence of an override.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#density).
public enum Density {

    /// Control heights 32, list rows 32. The toolkit's own values, so applying
    /// this applies nothing.
    REGULAR,

    /// Control heights 28, list rows 26.
    ///
    /// **Compact is below the 32×32 hit-target floor**, deliberately and only
    /// on the user's instruction: it is a preference a user sets, not a default
    /// an application picks. The glyph inside a control does not shrink with
    /// it; only the row around it does.
    COMPACT;

    /// The stylesheets that put this density in force, to be added **after** the
    /// theme.
    ///
    /// A list rather than a `Stylesheet` because [#REGULAR] genuinely has none,
    /// and an empty stylesheet returned to keep two shapes matching is a thing
    /// that parses, sorts and cascades every frame in order to do nothing.
    public List<Stylesheet> stylesheets() {
        return resourceName()
                .map(resource -> List.of(Stylesheet.parse(CascadeLayer.THEME, source(), ParseMode.STRICT, resource)))
                .orElseGet(List::of);
    }

    /// This density's resource name, e.g. `density-compact.css`, or empty for
    /// [#REGULAR].
    public Optional<String> resourceName() {
        return this == REGULAR
                ? Optional.empty()
                : Optional.of("density-" + name().toLowerCase(java.util.Locale.ROOT) + ".css");
    }

    /// This density's stylesheet text, as it ships — empty for [#REGULAR].
    ///
    /// Public for the reason [Controls#baseSource()] is: someone overriding a
    /// token should be able to read what they are overriding.
    public String source() {
        var resource = resourceName().orElse(null);
        if (resource == null) {
            return "";
        }
        try (InputStream in = Density.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the " + name() + " density is missing from the jar: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the " + name() + " density", e);
        }
    }
}
