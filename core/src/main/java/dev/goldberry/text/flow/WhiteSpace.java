package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Whether a paragraph may break a line the author did not: CSS's
/// `white-space`, cut down to the two behaviours a paragraph has.
///
/// ```css
/// text.name { white-space: nowrap }
/// .log      { white-space: pre-wrap }
/// ```
///
/// The whole of the difference is what a paragraph's measure function answers
/// when layout offers it a width. [#NORMAL] takes the offer and wraps inside it;
/// [#NOWRAP] reports the width the text actually wants and lets the box
/// overflow. Nothing else in the toolkit reads it.
///
/// CSS also has `pre`, `pre-wrap` and `pre-line`, and all three are statements
/// about collapsing: whether runs of spaces and newlines in the source survive
/// into the drawing. Goldberry never collapses anything, because a paragraph
/// draws the string it was handed. So the stylesheet may write all five
/// keywords and [#parse] reads them onto the two behaviours: `pre-wrap` and
/// `pre-line` are [#NORMAL], and `pre` is [#NOWRAP]. `pre-line` keeps runs of
/// spaces that CSS would fold into one; a log keeps its indentation either way.
///
/// A hard newline still breaks under `nowrap`. CSS's `nowrap` collapses `\n`
/// into a space; this does not. A paragraph keeps its explicit lines under
/// either value, and only soft wrapping, the search for somewhere to break
/// because the line would not fit, is what [#NOWRAP] turns off. The alternative
/// would be a paragraph whose text is not the string it was given.
///
/// Read more:
/// [Wrapping and cutting](https://goldberry.dev/docs/components/text.html#wrapping-and-cutting).
public enum WhiteSpace {

    /// Break lines wherever they will not fit, which is what a paragraph of prose
    /// wants and is CSS's initial value.
    NORMAL,

    /// Never break a line that was not already broken, however narrow the box.
    ///
    /// The box then overflows, and what happens to the overhang is
    /// [TextOverflow]'s question: cut off by an `overflow: hidden` ancestor,
    /// ended with an ellipsis, or simply drawn past the edge.
    NOWRAP;

    /// Whether a line too long for its box may be broken.
    public boolean wraps() {
        return this == NORMAL;
    }

    /// The keyword, or null when `name` is not one of CSS's five.
    ///
    /// `normal`, `pre-wrap` and `pre-line` wrap; `nowrap` and `pre` do not. See
    /// the type's documentation for why five spellings land on two values.
    public static @Nullable WhiteSpace parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "normal", "pre-wrap", "pre-line" -> NORMAL;
            case "nowrap", "pre" -> NOWRAP;
            default -> null;
        };
    }
}
