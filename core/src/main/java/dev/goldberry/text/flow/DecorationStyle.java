package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How a decoration's line is drawn: CSS's `text-decoration-style`.
///
/// ```css
/// a:hover          { text-decoration: underline dotted }
/// ::spelling-error { text-decoration: wavy underline var(--gb-danger) }
/// ```
///
/// Every style is drawn at the line's own thickness. A wave's height and a dot's
/// spacing grow with it, so a thicker line is a larger pattern rather than a
/// denser one.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public enum DecorationStyle {

    /// One straight line. The initial value.
    SOLID,

    /// Two straight lines, a thickness apart.
    DOUBLE,

    /// Round dots, a thickness apart.
    DOTTED,

    /// Dashes three thicknesses long.
    DASHED,

    /// A wave, as a spell checker draws under a word.
    WAVY;

    /// The keyword as CSS writes it.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// The style `name` spells, or null when it spells none of them.
    ///
    /// Null rather than an exception, for [TextDecoration#parse]'s reason: the
    /// caller is the cascade, and a value it does not know is a dropped
    /// declaration.
    public static @Nullable DecorationStyle parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }
}
