package dev.goldberry.css.select;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The pseudo-elements a stylesheet can write: `::placeholder`,
/// `::selection`, `::spelling-error` and `::grammar-error`.
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
    SELECTION,

    /// A word a spell checker did not know. It takes `color`, `background` and
    /// the `text-decoration` properties; the theme draws a wavy underline.
    SPELLING_ERROR,

    /// A stretch a grammar checker would flag. Reserved: CSS names it beside
    /// `::spelling-error`, and nothing stands for it yet.
    GRAMMAR_ERROR;

    /// The name as it is written after `::`.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
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
