package dev.goldberry.css.text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;
import dev.goldberry.text.flow.DecorationPaint;
import dev.goldberry.text.flow.DecorationStyle;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.flow.TextDecorations;

/// Reads `text-decoration`, its four longhands and `text-underline-offset`.
///
/// ```css
/// a                { text-decoration: underline dotted }
/// .misspelled      { text-decoration: wavy underline #e5484d 1.5px }
/// .link            { text-underline-offset: 3px }
/// ```
///
/// Every method answers empty for a value it cannot read whole, so the cascade
/// drops the declaration and says so, as it does for every other property. A
/// percentage is of the element's own font size, which is CSS's `1em`, and is
/// resolved here, where the size is known.
///
/// `currentcolor` needs nothing: the cascade has substituted the element's
/// colour for it before any value is read. A colour that is written stays
/// fixed, and one that is not follows the text, so a span in another colour
/// keeps its line in step with it.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public final class TextDecorationParser {

    private TextDecorationParser() {}

    /// The shorthand, its parts in any order and each at most once.
    ///
    /// A part that is not written is reset to its initial value, which is what
    /// makes a shorthand one: `text-decoration: underline` after
    /// `text-decoration: wavy red line-through` is a solid underline in the text's
    /// colour. `text-underline-offset` is not a part and is kept.
    ///
    /// @param current what the element has now, whose offset is kept
    public static Optional<TextDecorations> shorthand(
            List<Token> value, CssLength.Context context, TextDecorations current) {
        Set<TextDecoration> lines = null;
        DecorationStyle style = null;
        Integer colour = null;
        Double thickness = null;
        var parts = split(value);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        for (var i = 0; i < parts.size(); i++) {
            var part = parts.get(i);
            if (isLineKeyword(part)) {
                if (lines != null) {
                    return Optional.empty();
                }
                // The lines are a run of keywords: `underline line-through`.
                var end = i;
                while (end + 1 < parts.size() && isLineKeyword(parts.get(end + 1))) {
                    end++;
                }
                var run = new ArrayList<Token>();
                for (var k = i; k <= end; k++) {
                    run.addAll(parts.get(k));
                }
                var read = lines(run);
                if (read.isEmpty()) {
                    return Optional.empty();
                }
                lines = read.get();
                i = end;
                continue;
            }
            var asStyle = singleIdent(part).map(DecorationStyle::parse);
            if (asStyle.isPresent()) {
                if (style != null) {
                    return Optional.empty();
                }
                style = asStyle.get();
                continue;
            }
            var asThickness = thickness(part, context);
            if (asThickness.isPresent()) {
                if (thickness != null) {
                    return Optional.empty();
                }
                thickness = asThickness.get();
                continue;
            }
            var asColour = CssColor.parse(part);
            if (asColour == null || colour != null) {
                return Optional.empty();
            }
            colour = asColour;
        }
        var paint = new DecorationPaint(
                style == null ? DecorationStyle.SOLID : style,
                colour == null ? OptionalInt.empty() : OptionalInt.of(colour),
                thickness == null ? Double.NaN : thickness,
                current.paint().underlineOffset());
        return Optional.of(new TextDecorations(lines == null ? TextDecoration.NONE : lines, paint));
    }

    /// `text-decoration-line`: `none`, or one or both lines, each once.
    ///
    /// `none` beside another keyword is refused rather than resolved, as CSS
    /// says, and so is `overline` and `blink`: there is no overline here, and a
    /// declaration that asked for one and got a partial answer would lie.
    public static Optional<Set<TextDecoration>> lines(List<Token> value) {
        var tokens = withoutSpace(value);
        if (tokens.isEmpty() || tokens.stream().anyMatch(t -> !t.is(TokenType.IDENT))) {
            return Optional.empty();
        }
        if (tokens.size() == 1 && tokens.getFirst().isIdent("none")) {
            return Optional.of(TextDecoration.NONE);
        }
        var lines = EnumSet.noneOf(TextDecoration.class);
        for (var token : tokens) {
            var line = TextDecoration.parse(token.text());
            if (line == null || !lines.add(line)) {
                return Optional.empty();
            }
        }
        return Optional.of(Set.copyOf(lines));
    }

    /// `text-decoration-style`: one of the five keywords.
    public static Optional<DecorationStyle> style(List<Token> value) {
        return singleIdent(withoutSpace(value)).map(DecorationStyle::parse);
    }

    /// `text-decoration-color`: a colour. Empty is a dropped declaration, not
    /// the text's colour; that is what no declaration at all means.
    public static Optional<Integer> colour(List<Token> value) {
        return Optional.ofNullable(CssColor.parse(value));
    }

    /// `text-decoration-thickness`: `auto` and `from-font`, which are both the
    /// face's own thickness (`NaN`), or a length or percentage that is not
    /// negative.
    public static Optional<Double> thickness(List<Token> value, CssLength.Context context) {
        var tokens = withoutSpace(value);
        if (tokens.size() == 1
                && (tokens.getFirst().isIdent("auto") || tokens.getFirst().isIdent("from-font"))) {
            return Optional.of(Double.NaN);
        }
        return lengthOrPercent(value, context).filter(v -> v >= 0);
    }

    /// `text-underline-offset`: `auto` (`NaN`), or a length or percentage, which
    /// may be negative.
    public static Optional<Double> offset(List<Token> value, CssLength.Context context) {
        var tokens = withoutSpace(value);
        if (tokens.size() == 1 && tokens.getFirst().isIdent("auto")) {
            return Optional.of(Double.NaN);
        }
        return lengthOrPercent(value, context);
    }

    /// A length in logical pixels, with a percentage of the element's font size.
    /// `auto`, which `CssLength` reads as a length, is not one here.
    ///
    /// Handed the value with its spaces, which `calc()` needs around a `+`.
    private static Optional<Double> lengthOrPercent(List<Token> value, CssLength.Context context) {
        var tokens = withoutSpace(value);
        if (tokens.isEmpty() || (tokens.size() == 1 && tokens.getFirst().isIdent("auto"))) {
            return Optional.empty();
        }
        return switch (CssLength.parse(value, context)) {
            case Length.Points points -> Optional.of((double) points.value());
            case Length.Percent percent -> Optional.of(percent.value() / 100.0 * context.fontSize());
            case Length.Mixed mixed -> Optional.of(mixed.points() + mixed.percent() / 100.0 * context.fontSize());
            case null, default -> Optional.empty();
        };
    }

    private static boolean isLineKeyword(List<Token> part) {
        return singleIdent(part)
                .filter(name -> name.equalsIgnoreCase("none") || TextDecoration.parse(name) != null)
                .isPresent();
    }

    private static Optional<String> singleIdent(List<Token> part) {
        return part.size() == 1 && part.getFirst().is(TokenType.IDENT)
                ? Optional.of(part.getFirst().text())
                : Optional.empty();
    }

    private static List<Token> withoutSpace(List<Token> value) {
        return value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
    }

    /// `value` cut at the spaces between its parts, a function's arguments kept
    /// together: `rgb(229 72 77) 2px` is two parts.
    private static List<List<Token>> split(List<Token> value) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    parts.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }
}
