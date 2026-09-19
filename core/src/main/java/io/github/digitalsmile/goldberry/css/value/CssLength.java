package io.github.digitalsmile.goldberry.css.value;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.parse.TokenType;
import io.github.digitalsmile.goldberry.layout.Length;

/// Reads a CSS length into the [Length] Yoga takes.
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

    private CssLength() {}

    /// The context a relative length needs.
    ///
    /// Both fields are what the **root** resolves against, and each stops meaning
    /// that one step down: `em` becomes the element's own computed size inside
    /// [io.github.digitalsmile.goldberry.css.ComputedStyle#of]
    /// ([ADR-0242]), and `rem` becomes the root element's computed size, which the
    /// renderer puts here with [#withRootFontSize] once the root has resolved
    /// ([ADR-0416]).
    ///
    /// @param fontSize     the font size the root's own `em` resolves against
    /// @param rootFontSize the root element's computed font size, for `rem` —
    ///                     until the root has computed one, the configured value
    ///                     its own `font-size` declaration resolves `rem` against
    public record Context(float fontSize, float rootFontSize) {

        /// The default before typography tokens land (§10.1): 16 logical pixels,
        /// which is what every browser and every design system starts from.
        public static final Context DEFAULT = new Context(16, 16);

        /// This context with `rem` meaning `size`.
        ///
        /// The one thing the renderer changes about a context per frame. `em` is
        /// not withered beside it because nothing ever needs to be: the element's
        /// own size is derived per node where it is used, and a wither for it
        /// would be a second way to say something already said better.
        public Context withRootFontSize(float size) {
            return size == rootFontSize ? this : new Context(fontSize, size);
        }
    }

    /// Parses a length.
    ///
    /// @return the length, or null if these tokens are not one
    public static @Nullable Length parse(List<Token> value, Context context) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();

        if (token.isIdent("auto")) {
            return Length.AUTO;
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return Length.percent((float) token.numeric());
        }
        if (token.is(TokenType.NUMBER)) {
            // Unitless zero is the one number CSS accepts as a length, because
            // "0" has no direction to be wrong about. Anything else unitless is
            // an author error worth surfacing rather than guessing px for.
            return token.numeric() == 0 ? Length.points(0) : null;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit()) {
            case "px" -> Length.points((float) token.numeric());
            case "em" -> Length.points((float) (token.numeric() * context.fontSize()));
            case "rem" -> Length.points((float) (token.numeric() * context.rootFontSize()));
            default -> null;
        };
    }

    /// Parses a plain number — `flex-grow: 1`, `opacity: 0.5`.
    ///
    /// A percentage is accepted for `opacity`, where CSS allows both spellings,
    /// and divided into the 0..1 the property means.
    ///
    /// @return the number, or null if these tokens are not one
    public static @Nullable Double parseNumber(List<Token> value) {
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
