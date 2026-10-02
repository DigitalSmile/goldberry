package dev.goldberry.text.flow;

import java.util.Set;

/// A rule drawn along a line of text: CSS's `text-decoration-line`.
///
/// ```css
/// link { text-decoration: underline }
/// .done { text-decoration: line-through }
/// ```
///
/// Only the line part of CSS's `text-decoration` shorthand is here. The shorthand
/// can also carry a colour and a style (`wavy`, `dotted`); a colour would be a
/// second colour on a box that already has one, and a style needs a dashed
/// stroke along a rule one pixel thick, so neither is built.
///
/// The toolkit draws the rule itself because a rule under text needs the face's
/// own `underlinePosition` and `underlineThickness`, which are in the font file
/// and not reachable from a paragraph's public surface. A rectangle drawn under
/// the text from outside would get the thickness wrong at every size and the
/// position wrong at every family.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public enum TextDecoration {

    /// A rule under the text, at the face's own underline position.
    UNDERLINE,

    /// A rule through it, at the face's own strikethrough position. CSS's own
    /// spelling, rather than `strikethrough`: a stylesheet writes
    /// `text-decoration: line-through`.
    LINE_THROUGH;

    /// Nothing drawn: CSS's `none`, and the initial value.
    public static final Set<TextDecoration> NONE = Set.of();

    /// The name as it is written in CSS: `line-through`, not `LINE_THROUGH`.
    public String cssName() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /// The decoration `name` spells, or null when it spells none of them.
    ///
    /// Null rather than an exception, because the caller is the cascade and a value
    /// it does not recognise is a dropped declaration with a warning rather than
    /// a stylesheet that fails to parse, the rule every other property follows.
    public static @org.jspecify.annotations.Nullable TextDecoration parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }
}
