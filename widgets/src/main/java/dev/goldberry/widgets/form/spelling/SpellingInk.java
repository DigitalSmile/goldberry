package dev.goldberry.widgets.form.spelling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.background.Background;
import dev.goldberry.paint.Box;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.text.flow.DecorationPaint;
import dev.goldberry.text.flow.DecorationStyle;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.spell.SpellCheck;

/// How a misspelled word is drawn, as `::spelling-error` resolved it, and the
/// spans that draw a field's misspelled words that way.
///
/// @param colour     the word's own colour when the sheet gave it one, or empty
///                   to keep the field's
/// @param lines      the decoration lines
/// @param paint      how they are drawn
/// @param background what is behind the word, or 0 for nothing
public record SpellingInk(OptionalInt colour, Set<TextDecoration> lines, DecorationPaint paint, int background) {

    /// What a field draws when there is no `::spelling-error` box to read: a
    /// wavy red underline.
    public static final SpellingInk FALLBACK = new SpellingInk(
            OptionalInt.empty(),
            Set.of(TextDecoration.UNDERLINE),
            DecorationPaint.SOLID.style(DecorationStyle.WAVY).colour(0xFFE5484D),
            0);

    public SpellingInk {
        Objects.requireNonNull(colour, "colour");
        lines = Set.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(paint, "paint");
    }

    /// What the [dev.goldberry.widgets.form.parts.SpellingError] part's box
    /// says, its colour kept only when it differs from `fieldColour`, the
    /// colour the part inherited.
    public static SpellingInk of(@Nullable Box part, int fieldColour) {
        if (part == null || part.text() == null) {
            return FALLBACK;
        }
        var text = part.text();
        var flow = text.flow();
        var background = part.fill() instanceof Background.Colour(var argb) ? argb : 0;
        return new SpellingInk(
                text.argb() == fieldColour ? OptionalInt.empty() : OptionalInt.of(text.argb()),
                flow.decorations(),
                flow.decorationPaint(),
                background);
    }

    /// The spans that draw the misspelled words of `text` between `from` and
    /// `to`, counted from `from`, over text drawn in `ink`.
    ///
    /// `text` is the whole of what the field holds, with its hard lines, and
    /// `caret` an offset into it or negative: the word the caret is in is not
    /// flagged.
    public List<SpanPaint> spans(String text, int from, int to, SpellCheck check, int caret, int ink) {
        var spans = new ArrayList<SpanPaint>();
        var argb = colour.orElse(ink);
        var lineStart = from == 0 ? 0 : text.lastIndexOf('\n', from - 1) + 1;
        while (true) {
            var newline = text.indexOf('\n', lineStart);
            var lineEnd = newline < 0 ? text.length() : newline;
            var line = text.substring(lineStart, lineEnd);
            var caretInLine = caret >= lineStart && caret <= lineEnd ? caret - lineStart : -1;
            for (var word : SpellCheck.outside(check.misspellings(line), caretInLine)) {
                var start = Math.max(from, lineStart + word.start());
                var end = Math.min(to, lineStart + word.end());
                if (end > start) {
                    spans.add(new SpanPaint(start - from, end - from, argb, lines, background, paint));
                }
            }
            if (newline < 0 || newline + 1 >= to) {
                break;
            }
            lineStart = newline + 1;
        }
        return List.copyOf(spans);
    }
}
