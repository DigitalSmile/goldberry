package io.github.digitalsmile.goldberry.text.flow;

/// What is drawn where a line runs past the box it is in — CSS's
/// `text-overflow`.
///
/// Only meaningful beside [WhiteSpace#NOWRAP], and for CSS's reason rather than
/// an implementation one: a line that is allowed to wrap never overflows, so
/// there is nothing to mark. A stylesheet that writes `text-overflow: ellipsis`
/// and no `white-space: nowrap` gets a wrapped paragraph and no ellipsis, which
/// is what every browser does with the same two rules.
///
/// ## It is a paint decision and never a layout one
///
/// A truncated line is drawn short and **measured long**: the measure function
/// reports the width the untruncated text wants, exactly as it does under
/// [#CLIP]. That is not an omission. A paragraph whose measurement shrank because
/// it had been ellipsised would be a box that shrank because it was too narrow —
/// which either settles a size nobody asked for or oscillates, and either way
/// makes the ellipsis decide the width it was supposed to be a consequence of
/// ([ADR-0255]).
public enum TextOverflow {

    /// Draw the line at its full width and let whatever is above it decide.
    ///
    /// With an `overflow: hidden` ancestor that is a cut label; with none it is a
    /// label hanging out of its box, which is what the catalog did before
    /// [WhiteSpace] existed and is still the initial value.
    CLIP,

    /// End the line with [#MARK] at the last place it fits.
    ELLIPSIS;

    /// The character an [#ELLIPSIS] draws — U+2026 HORIZONTAL ELLIPSIS, one glyph
    /// rather than three full stops.
    ///
    /// One glyph because the fallback chain can find it in Inter and in JetBrains
    /// Mono, and because three periods measure wider than the ellipsis every font
    /// draws for them — so a label truncated with `...` would leave a gap where
    /// one more letter would have fitted.
    public static final String MARK = "…";

    /// Whether a line too long for its box is ended with [#MARK].
    public boolean marks() {
        return this == ELLIPSIS;
    }
}
