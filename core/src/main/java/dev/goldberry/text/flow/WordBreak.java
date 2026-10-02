package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Where a line may break inside a word: CSS's `word-break`.
///
/// ```css
/// .hash { word-break: break-all }
/// ```
///
/// Under [#NORMAL] a line breaks only between words. Under [#BREAK_ALL] it may
/// break between any two grapheme clusters, so every line is filled to the
/// edge and a long token is cut wherever the edge falls. That suits a column of
/// identifiers or hex, where a word boundary means nothing; prose wants
/// [OverflowWrap#ANYWHERE] instead, which breaks a word only when it cannot fit
/// on a line of its own.
///
/// `keep-all` is not in the subset: it changes breaking only for CJK text,
/// which the line breaker does not treat differently yet.
///
/// Read more:
/// [Wrapping and cutting](https://goldberry.dev/docs/components/text.html#wrapping-and-cutting).
public enum WordBreak {

    /// Lines break between words, which is CSS's initial value.
    NORMAL,

    /// Lines may break between any two grapheme clusters.
    BREAK_ALL;

    /// The keyword, or null when `name` is not `normal` or `break-all`.
    public static @Nullable WordBreak parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "normal" -> NORMAL;
            case "break-all" -> BREAK_ALL;
            default -> null;
        };
    }
}
