package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;

/// Reads a CSS length into the [StyleLength] Yoga takes.
///
/// ## Units
///
/// `px`, `%`, `em`, `rem` and the keyword `auto`, per §8. `px` is a **logical**
/// pixel — the window's scale is applied by the paint context, not here
/// (ADR-0031), so a length in a stylesheet means the same thing on a 150%
/// display as on a 100% one.
///
/// `em` and `rem` are resolved at parse time against a font size rather than
/// carried as units into Yoga, because Yoga has no concept of a font size.
/// That makes them relative to the font size *in force where the declaration
/// lands*, which is what CSS says and what a caller must supply.
///
/// `calc()` is deferred (§8) — it needs an expression tree and a resolution pass
/// that knows the containing block, and no stylesheet here has asked for one.
public final class CssLength {

    private CssLength() {
    }

    /// The context a relative length needs.
    ///
    /// @param fontSize     the font size in force, for `em`
    /// @param rootFontSize the root element's font size, for `rem`
    public record Context(float fontSize, float rootFontSize) {

        /// The default before typography tokens land (§10.1): 16 logical pixels,
        /// which is what every browser and every design system starts from.
        public static final Context DEFAULT = new Context(16, 16);
    }

    /// Parses a length.
    ///
    /// @return the length, or null if these tokens are not one
    public static StyleLength parse(List<Token> value, Context context) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();

        if (token.isIdent("auto")) {
            return StyleLength.AUTO;
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return StyleLength.percent((float) token.numeric());
        }
        if (token.is(TokenType.NUMBER)) {
            // Unitless zero is the one number CSS accepts as a length, because
            // "0" has no direction to be wrong about. Anything else unitless is
            // an author error worth surfacing rather than guessing px for.
            return token.numeric() == 0 ? StyleLength.points(0) : null;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit()) {
            case "px" -> StyleLength.points((float) token.numeric());
            case "em" -> StyleLength.points((float) (token.numeric() * context.fontSize()));
            case "rem" -> StyleLength.points((float) (token.numeric() * context.rootFontSize()));
            default -> null;
        };
    }

    /// Parses a plain number — `flex-grow: 1`, `opacity: 0.5`.
    ///
    /// A percentage is accepted for `opacity`, where CSS allows both spellings,
    /// and divided into the 0..1 the property means.
    ///
    /// @return the number, or null if these tokens are not one
    public static Double parseNumber(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.NUMBER)) {
            return token.numeric();
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return token.numeric() / 100;
        }
        return null;
    }
}
