package dev.goldberry.layout;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Which way a box's inline axis runs: CSS's `direction`.
///
/// It decides which side is a row's start, what `text-align: start` and the
/// logical properties (`margin-inline-start`, `padding-inline`) mean, and the
/// base direction a paragraph is aligned in. It inherits.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public enum Direction {

    /// Left to right: a row starts at the left. The initial value.
    LTR,

    /// Right to left: a row starts at the right, and so does a line of text.
    RTL;

    /// The keyword, or null: `ltr` or `rtl`.
    public static @Nullable Direction parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "ltr" -> LTR;
            case "rtl" -> RTL;
            default -> null;
        };
    }
}
