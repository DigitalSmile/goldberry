package dev.goldberry.css.select;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The pseudo-elements a stylesheet can write: `::placeholder` and
/// `::selection`.
///
/// ```css
/// text-input::placeholder { color: var(--gb-text-muted) }
/// ::selection             { background: var(--gb-accent-bg-hover) }
/// ```
///
/// Each one is a **part** under another name. A part is a node of its own,
/// with a type the cascade selects and properties it takes; the part that
/// stands for a pseudo-element says so through
/// [dev.goldberry.css.StyleElement#pseudoElement()], and a selector ending in
/// `::placeholder` matches that part below the element before the `::`.
/// `text-input::placeholder` and `text-input text-value.placeholder` select the
/// same node.
///
/// A closed set, as the pseudo-classes are: `::before`, `::after` and the
/// rest have no part to stand for, and a sheet that writes one is refused.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#parts).
public enum PseudoElement {

    /// The text a field shows while it holds no value.
    PLACEHOLDER,

    /// The wash behind selected text. It takes `background`; the text keeps
    /// its own colour, so a selection stays readable.
    SELECTION;

    /// The name as it is written after `::`.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// The pseudo-element called `name`, or null for one outside the set.
    public static @Nullable PseudoElement parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }
}
