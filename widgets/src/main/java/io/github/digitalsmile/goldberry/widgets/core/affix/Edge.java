package io.github.digitalsmile.goldberry.widgets.core.affix;

import java.util.Locale;
import java.util.regex.Pattern;

/// Which side of the viewport an [Affix] pins itself to — §1's `edge=`.
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
    public static Edge parse(String name) {
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
    /// names one, or a second on the same axis as the first (ADR-0371).
    @SuppressWarnings("StringSplitter") // trimmed first, so there is no empty leading word
    public static Edge parseCross(String name) {
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
