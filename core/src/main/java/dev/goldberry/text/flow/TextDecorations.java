package dev.goldberry.text.flow;

import java.util.Objects;
import java.util.Set;

/// The whole of `text-decoration` as the cascade holds it: which lines are drawn,
/// and how they look.
///
/// ```css
/// .misspelled { text-decoration: underline wavy #e5484d 1.5px }
/// ```
///
/// One value rather than two components. CSS propagates a decoration from the
/// box that declared it to the text inside, and propagates it whole: a child
/// that inherits the line also inherits its pattern and its colour. Here
/// propagation is inheritance (see [TextFlow]), and a single component
/// inherits as one.
///
/// @param lines the lines, as a set because CSS allows both at once
/// @param paint how they are drawn
public record TextDecorations(Set<TextDecoration> lines, DecorationPaint paint) {

    /// No lines, drawn as CSS draws them by default: the initial value.
    public static final TextDecorations NONE = new TextDecorations(TextDecoration.NONE, DecorationPaint.SOLID);

    public TextDecorations {
        lines = Set.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(paint, "paint");
    }

    /// This, with other lines and the same paint: `text-decoration-line`.
    public TextDecorations lines(Set<TextDecoration> value) {
        return new TextDecorations(value, paint);
    }

    /// This, with the same lines in another paint.
    public TextDecorations paint(DecorationPaint value) {
        return new TextDecorations(lines, value);
    }

    /// Whether any line is drawn.
    public boolean isDecorated() {
        return !lines.isEmpty();
    }
}
