package dev.goldberry.text.font;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// Where a font looks for the characters its own face has no glyph for.
///
/// ```java
/// Font body = fonts.of(BundledFont.UI, 14);
/// Font han = body.fallbacks().fontFor("中文", 0, 1);   // a fallback face at 14, or null
/// ```
///
/// A [Fonts] book attaches one to every font it opens when the application
/// has fallback faces, and opens each fallback face the first time a character
/// needs it rather than when the font is opened. [#of(List)] is the form for a
/// font made by hand. A paragraph asks once per cluster its own face cannot
/// draw, and never for text the face covers.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public interface Fallbacks {

    /// No fallback faces: a character the font lacks is drawn as `.notdef`.
    Fallbacks NONE = new Fallbacks() {

        @Override
        public @Nullable Font fontFor(CharSequence text, int start, int end) {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public String toString() {
            return "Fallbacks.NONE";
        }
    };

    /// The font, at the size of the font this belongs to, that draws the
    /// cluster `[start, end)` of `text`, or null when no fallback face can.
    ///
    /// The first face, in order, with a glyph for every character of the
    /// cluster that needs one; failing that, the first with a glyph for its
    /// first character, so a letter is drawn even where a mark on it is not.
    ///
    /// @throws IndexOutOfBoundsException if the range is not within the text
    @Nullable
    Font fontFor(CharSequence text, int start, int end);

    /// Whether there is nowhere to look. A paragraph whose font answers true
    /// takes the path it took before fallbacks existed, without reading the
    /// face's coverage at all.
    default boolean isEmpty() {
        return false;
    }

    /// Fonts already open, searched in the order given.
    ///
    /// The fonts are not owned: whoever opened them closes them. They should be
    /// at the size of the font this is attached to, because a fallback's glyphs
    /// are drawn at their own font's size.
    static Fallbacks of(List<Font> fonts) {
        var held = List.copyOf(Objects.requireNonNull(fonts, "fonts"));
        if (held.isEmpty()) {
            return NONE;
        }
        return (text, start, end) -> {
            Objects.checkFromToIndex(start, end, text.length());
            if (start == end) {
                return null;
            }
            var first = Character.codePointAt(text, start);
            Font firstOnly = null;
            for (var font : held) {
                var coverage = font.face().coverage();
                if (coverage.coversAll(text, start, end)) {
                    return font;
                }
                if (firstOnly == null && coverage.covers(first)) {
                    firstOnly = font;
                }
            }
            return firstOnly;
        };
    }
}
