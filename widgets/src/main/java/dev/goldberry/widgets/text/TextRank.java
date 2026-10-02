package dev.goldberry.widgets.text;

import java.util.Locale;

/// One rank of the type scale, as a value: what `text style="title"` and
/// `text class="title"` both mean.
///
/// In Java, `new Text("…").style(TextRank.TITLE)`. The rank is **not** a size.
/// What `heading` is worth is the theme's, in `controls.css`, which is what
/// lets a large-text theme move every rank at once without a widget hearing
/// about it.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#the-type-scale).
public enum TextRank {
    DISPLAY,

    TITLE,

    HEADING,

    BODY,

    /// `body` at weight 600: a class beside `body` rather than a seventh size.
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
    /// @throws IllegalArgumentException if it is not one of the ranks, which is a
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
            throw new IllegalArgumentException("\"" + name + "\" is not a type rank. The ranks are: " + known + ".", e);
        }
    }
}
