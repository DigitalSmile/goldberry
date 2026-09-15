package io.github.digitalsmile.goldberry.text.flow;

import java.util.Set;

/// A rule drawn along a line of text — CSS's `text-decoration-line`.
///
/// The third thing `text-decoration` could mean and the only one that is a
/// *line*: CSS's shorthand also carries a colour and a style (`wavy`, `dotted`),
/// and neither is here. A colour would be a second colour on a box that already
/// has one and is worth it only for a spell-checker's squiggle; a style needs a
/// dashed stroke along a rule one pixel thick, which is a path rather than a
/// rectangle. Both are additions rather than corrections, and the subset in
/// `docs/core-widgets.md` §8 says which properties exist (ADR-0321).
///
/// ## Why the toolkit owns it and an application cannot
///
/// A rule under text needs the line's extents and the **face's own**
/// `underlinePosition` and `underlineThickness`, which are in the font file and
/// are not reachable from a
/// [io.github.digitalsmile.goldberry.text.Paragraph]'s public surface. Drawing a
/// rectangle under the text from outside would get the thickness wrong at every
/// size and the position wrong at every family — which is `docs/gaps.md` G27, and
/// why the answer is here rather than in the application that asked.
public enum TextDecoration {

    /// A rule under the text, at the face's own underline position.
    UNDERLINE,

    /// A rule through it, at the face's own strikethrough position. CSS's own
    /// spelling, rather than `strikethrough`: a stylesheet writes
    /// `text-decoration: line-through`.
    LINE_THROUGH;

    /// Nothing drawn — CSS's `none`, and what every box in the catalog had before
    /// this existed.
    public static final Set<TextDecoration> NONE = Set.of();

    /// The name as it is written in CSS — `line-through`, not `LINE_THROUGH`.
    public String cssName() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /// The decoration `name` spells, or null when it spells none of them.
    ///
    /// Null rather than an exception, because the caller is the cascade and a value
    /// it does not recognise is a **dropped declaration** with a warning rather
    /// than a stylesheet that fails to parse — the rule every other property here
    /// follows.
    public static @org.jspecify.annotations.Nullable TextDecoration parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }
}
