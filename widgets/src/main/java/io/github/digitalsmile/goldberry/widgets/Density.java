package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/// How tall a control is — `docs/design-system.md` §1.3's density preference.
///
/// A user preference applied app-wide, and §1.3's promise is that
/// "token-conformant apps adapt with zero code". That promise is kept by
/// [Controls#baseStylesheet()] sizing every control from `--gb-control-height`
/// rather than from a literal, so switching a density is a stylesheet swap in
/// the [CascadeLayer#THEME] slot and no widget learns which one answered — the
/// same mechanism, and the same slot, as switching a theme
/// ([io.github.digitalsmile.goldberry.css.Theme]).
///
/// It lives in `:widgets` rather than beside `Theme` in `:core` because a
/// density sizes *controls*, and `:core`'s primitives have no height for one to
/// move. A theme is in `:core` for the opposite reason: `row` and `text` read
/// `--gb-bg` and `--gb-text` too.
///
/// ## Why [#REGULAR] ships no stylesheet
///
/// Regular is not something an application applies. It is what the toolkit
/// already is — the numbers are in `controls.css` with every other §3 metric —
/// so [#stylesheets()] is empty for it, and there is no `density-regular.css`
/// restating 32 in a second file for the two to drift apart in.
///
/// The asymmetry is the fact rather than an omission: §1.3 spells regular
/// "(default)", and a default is the absence of an override.
public enum Density {

    /// Control heights 32, list rows 32 (§1.3). The toolkit's own values, so
    /// applying this applies nothing.
    REGULAR,

    /// Control heights 28, list rows 26 (§1.3).
    ///
    /// **Compact is below §1.3's own 32×32 hit-target floor**, deliberately and
    /// on the user's instruction — see ADR-0074. The glyph inside a control does
    /// not shrink with it; only the row around it does.
    COMPACT;

    /// The stylesheets that put this density in force, to be added **after** the
    /// theme.
    ///
    /// A list rather than a `Stylesheet` because [#REGULAR] genuinely has none,
    /// and an empty stylesheet returned to keep two shapes matching is a thing
    /// that parses, sorts and cascades every frame in order to do nothing.
    public List<Stylesheet> stylesheets() {
        return resourceName()
                .map(resource -> List.of(Stylesheet.parse(CascadeLayer.THEME, source())))
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
                throw new IllegalStateException(
                        "the " + name() + " density is missing from the jar: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the " + name() + " density", e);
        }
    }
}
