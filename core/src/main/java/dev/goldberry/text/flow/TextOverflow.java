package dev.goldberry.text.flow;

/// What is drawn where a line runs past the box it is in: CSS's
/// `text-overflow`.
///
/// ```css
/// text.name { white-space: nowrap; text-overflow: ellipsis }
/// ```
///
/// Only meaningful beside [WhiteSpace#NOWRAP], for CSS's own reason: a line that
/// is allowed to wrap never overflows, so there is nothing to mark. A stylesheet
/// that writes `text-overflow: ellipsis` and no `white-space: nowrap` gets a
/// wrapped paragraph and no ellipsis, which is what every browser does with the
/// same two rules.
///
/// Truncation is a paint decision and never a layout one. A truncated line is
/// drawn short and measured long: the measure function reports the width the
/// untruncated text wants, exactly as it does under [#CLIP]. A paragraph whose
/// measurement shrank because it had been ellipsised would be a box that shrank
/// because it was too narrow, which either settles a size nobody asked for or
/// oscillates; either way the ellipsis would decide the width it was supposed
/// to be a consequence of.
///
/// Read more:
/// [Wrapping and cutting](https://goldberry.dev/docs/components/text.html#wrapping-and-cutting).
public enum TextOverflow {

    /// Draw the line at its full width and let whatever is above it decide.
    ///
    /// With an `overflow: hidden` ancestor that is a cut label; with none it is a
    /// label hanging out of its box. This is the initial value.
    CLIP,

    /// End the line with [#MARK] at the last place it fits.
    ELLIPSIS;

    /// The character an [#ELLIPSIS] draws: U+2026 HORIZONTAL ELLIPSIS, one glyph
    /// rather than three full stops.
    ///
    /// One glyph because both bundled families have it, and because three periods
    /// measure wider than the ellipsis every font draws for them, so a label
    /// truncated with `...` would leave a gap where one more letter would have
    /// fitted.
    public static final String MARK = "…";

    /// Whether a line too long for its box is ended with [#MARK].
    public boolean marks() {
        return this == ELLIPSIS;
    }
}
