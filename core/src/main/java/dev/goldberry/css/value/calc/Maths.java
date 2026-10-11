package dev.goldberry.css.value.calc;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;

/// A math function where a reader expects one value: parsed and folded under a
/// length context in one step.
///
/// ```java
/// Quantity q = Maths.fold(tokens, context); // round(up, 250ms, 100ms)
/// ```
///
/// What each reader of a number, an angle or a time asks before it reads the
/// token itself. Null when the tokens are not a math function, or are one that
/// does not fold; the reader tells the two apart with [CalcParser#starts] when it
/// needs to.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class Maths {

    private Maths() {}

    /// `value` folded, or null when it is not one math function or does not
    /// fold.
    public static @Nullable Quantity fold(List<Token> value, CssLength.Context context) {
        var tree = CalcParser.parse(value);
        return tree == null ? null : Folding.fold(tree, ContextUnits.of(context));
    }

    /// Whether `value` is a math function at all, folding or not: a reader that
    /// would otherwise try the tokens as a plain value refuses them instead.
    public static boolean isMath(List<Token> value) {
        for (var token : value) {
            if (!token.is(TokenType.WHITESPACE)) {
                return CalcParser.starts(token);
            }
        }
        return false;
    }
}
