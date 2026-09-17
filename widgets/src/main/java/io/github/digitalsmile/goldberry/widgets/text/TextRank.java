package io.github.digitalsmile.goldberry.widgets.text;

import java.util.Locale;

/// One rank of §1.4's type scale, as a value.
///
/// `docs/core-widgets.md` §2 gives `text` a `style=` attribute for the token
/// styles — `text style="title"` — and what shipped was `class="title"`: the
/// same thing, spelled the way CSS already spells it. Both spellings exist now
/// and this is what they both mean ([ADR-0381]).
///
/// The rank is **not** a size. What `heading` is worth is the theme's, in
/// `controls.css`, which is what lets a large-text theme move every rank at once
/// without a widget hearing about it.
public enum TextRank {
    DISPLAY,

    TITLE,

    HEADING,

    BODY,

    /// `body` at 600. A class beside `body` rather than a seventh size, which is
    /// how §1.4 writes it too.
    BODY_STRONG,

    CAPTION,

    MONO;

    /// The class this rank is, which is the rank's name as CSS spells it:
    /// `BODY_STRONG` is `body-strong`.
    public String cssClass() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// The rank `name` is, in either spelling — `body-strong` and `BODY_STRONG`
    /// are the same rank.
    ///
    /// @throws IllegalArgumentException if it is not one of the six, which is a
    ///         typo in a document and is worth saying so rather than resolving
    ///         to a class no rule matches
    public static TextRank of(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            var known = new StringBuilder();
            for (var rank : values()) {
                known.append(known.isEmpty() ? "" : ", ").append(rank.cssClass());
            }
            throw new IllegalArgumentException(
                    "\"" + name + "\" is not one of §1.4's type ranks. They are: " + known + ".", e);
        }
    }
}
