package dev.goldberry.text.mark;

import java.util.Objects;
import java.util.Set;

import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.font.Font;

/// How words under a set of marks look: the font they are shaped in, and the
/// colour, rules and fill they are drawn with.
///
/// ```java
/// MarkStyles styles = kinds -> kinds.contains(MarkKind.Style.BOLD)
///         ? new MarkStyle(bold, MarkStyle.TEXT_COLOUR, Set.of(), 0)
///         : MarkStyle.plain(regular);
/// editor.markStyles(styles);
/// ```
///
/// The font decides the glyphs, so changing it shapes the words again; the
/// colour, the rules and the fill are paint, and change nothing about where a
/// glyph goes.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param font        the face and size the words are shaped in
/// @param argb        their colour, `0xAARRGGBB` and not premultiplied, or
///                    [#TEXT_COLOUR] for the colour the rest of the text has
/// @param decorations the rules drawn along them, usually none
/// @param background  a fill behind them, or 0 for none
public record MarkStyle(Font font, int argb, Set<TextDecoration> decorations, int background) {

    /// The colour the text around the marked words is drawn in. Zero, which as a
    /// colour would draw nothing at all.
    public static final int TEXT_COLOUR = 0;

    public MarkStyle {
        Objects.requireNonNull(font, "font");
        decorations = Set.copyOf(Objects.requireNonNull(decorations, "decorations"));
    }

    /// Words in `font`, drawn like the text around them.
    public static MarkStyle plain(Font font) {
        return new MarkStyle(font, TEXT_COLOUR, Set.of(), 0);
    }

    /// Whether drawing these words differs from drawing the text around them in
    /// anything but the font.
    public boolean paints() {
        return argb != TEXT_COLOUR || !decorations.isEmpty() || background != 0;
    }
}
