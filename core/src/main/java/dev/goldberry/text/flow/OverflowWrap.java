package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Whether a word too wide for a whole line may be broken inside: CSS's
/// `overflow-wrap`.
///
/// ```css
/// .log { white-space: pre-wrap; overflow-wrap: anywhere }
/// ```
///
/// A paragraph breaks lines between words. A single word wider than the box,
/// such as a long path or a hash in a log line, has nowhere to break, and under
/// [#NORMAL] it overflows on a line of its own. Under [#ANYWHERE] it is broken
/// between two grapheme clusters, as late as the line allows, and the rest
/// carries on to the next line.
///
/// Only a word that does not fit on a line **of its own** is broken. A word
/// that merely does not fit after the words before it moves to the next line,
/// as it always did. That is the difference from [WordBreak#BREAK_ALL].
///
/// CSS's `break-word` reads as [#ANYWHERE]. The two differ in CSS only in what
/// they report as the box's smallest width, and a paragraph here reports its
/// widest line at the width it was offered.
///
/// Read more:
/// [Wrapping and cutting](https://goldberry.dev/docs/components/text.html#wrapping-and-cutting).
public enum OverflowWrap {

    /// A word wider than the line overflows it, which is CSS's initial value.
    NORMAL,

    /// A word wider than the line is broken between grapheme clusters.
    ANYWHERE;

    /// The keyword, or null when `name` is not one: `normal`, `anywhere`, or
    /// `break-word`, which is read as `anywhere`.
    public static @Nullable OverflowWrap parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "normal" -> NORMAL;
            case "anywhere", "break-word" -> ANYWHERE;
            default -> null;
        };
    }
}
