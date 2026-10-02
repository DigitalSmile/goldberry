package dev.goldberry.widgets.core.affix;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// Which side of the viewport an [Affix] pins itself to: the `edge=` attribute.
///
/// `edge="top left"` names one edge per axis; [#parse] reads the first word and
/// [#parseCross] the second.
///
/// Read more: [Affix](https://goldberry.dev/docs/layout/affix.html#two-axes).
public enum Edge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT;

    /// What separates two edges in `edge="top left"`.
    private static final Pattern WORDS = Pattern.compile("\\s+");

    /// Whether this edge pins along the vertical axis.
    public boolean isVertical() {
        return this == TOP || this == BOTTOM;
    }

    /// Whether this edge is the near one — the top or the left, where the
    /// comparison is "has it gone above/before the viewport".
    public boolean isNear() {
        return this == TOP || this == LEFT;
    }

    /// The edge `name` spells, defaulting to [#TOP] rather than throwing: a
    /// document that misspells an attribute should still show its content, which
    /// is the registry's rule everywhere else.
    @SuppressWarnings("StringSplitter") // trimmed first, so there is no empty leading word
    public static Edge parse(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return TOP;
        }
        return switch (WORDS.split(name.trim())[0].toLowerCase(Locale.ROOT)) {
            case "bottom" -> BOTTOM;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            default -> TOP;
        };
    }

    /// The second edge `name` spells — `left` in `"top left"` — or null when it
    /// names only one, or a second on the same axis as the first.
    @SuppressWarnings("StringSplitter") // trimmed first, so there is no empty leading word
    public static @Nullable Edge parseCross(@Nullable String name) {
        if (name == null) {
            return null;
        }
        var words = WORDS.split(name.trim());
        if (words.length < 2) {
            return null;
        }
        var first = parse(words[0]);
        var second = parse(words[1]);
        return second.isVertical() == first.isVertical() ? null : second;
    }
}
