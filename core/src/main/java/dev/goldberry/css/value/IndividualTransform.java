package dev.goldberry.css.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.Transform.Function;

/// CSS's `translate`, `rotate` and `scale` properties: three transforms a
/// rule can set apart from `transform` and from one another.
///
/// ```css
/// .card:hover  { translate: 0 -2px }
/// .chevron[open] { rotate: 90deg }
/// .tick        { scale: 0.6; transition: scale var(--gb-motion-fast) ease-enter }
/// ```
///
/// They apply before `transform` does, translate first, then rotate, then
/// scale, about the same origin; and each animates by itself, which is what
/// lets a hover lift a card that a keyframe is turning.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transform).
///
/// @param translate `translate`, or null for `none`
/// @param rotate    `rotate`, or null for `none`
/// @param scale     `scale`, or null for `none`
public record IndividualTransform(
        Function.@Nullable Translate translate, Function.@Nullable Rotate rotate, Function.@Nullable Scale scale) {

    /// None of the three.
    public static final IndividualTransform NONE = new IndividualTransform(null, null, null);

    /// Whether none of the three is set.
    public boolean isNone() {
        return translate == null && rotate == null && scale == null;
    }

    /// The three as `transform` functions, in the order CSS writes their
    /// effect: translate, rotate, scale.
    public List<Function> functions() {
        var functions = new ArrayList<Function>(3);
        if (translate != null) {
            functions.add(translate);
        }
        if (rotate != null) {
            functions.add(rotate);
        }
        if (scale != null) {
            functions.add(scale);
        }
        return functions;
    }

    /// This, with `translate` replaced.
    public IndividualTransform translate(Function.@Nullable Translate value) {
        return new IndividualTransform(value, rotate, scale);
    }

    /// This, with `rotate` replaced.
    public IndividualTransform rotate(Function.@Nullable Rotate value) {
        return new IndividualTransform(translate, value, scale);
    }

    /// This, with `scale` replaced.
    public IndividualTransform scale(Function.@Nullable Scale value) {
        return new IndividualTransform(translate, rotate, value);
    }

    /// This `t` of the way to `to`, each of the three by itself, `none` being
    /// the identity of the other side.
    public IndividualTransform mix(IndividualTransform to, double t) {
        return new IndividualTransform(
                (Function.Translate) mixed(translate, to.translate, t),
                (Function.Rotate) mixed(rotate, to.rotate, t),
                (Function.Scale) mixed(scale, to.scale, t));
    }

    private static @Nullable Function mixed(@Nullable Function from, @Nullable Function to, double t) {
        if (from == null && to == null) {
            return null;
        }
        var start = from == null ? Objects.requireNonNull(to).identity() : from;
        var end = to == null ? start.identity() : to;
        return start.mix(end, t);
    }

    /// What a declaration of one of the three reads as: the function, or none.
    ///
    /// @param function the value, or null for `none`
    public record Parsed<T extends Function>(@Nullable T function) {}

    /// `translate: x [y]`, lengths or percentages of the box, or `none`.
    public static @Nullable Parsed<Function.Translate> parseTranslate(List<Token> value, CssLength.Context context) {
        var words = words(value);
        if (isNone(words)) {
            return new Parsed<>(null);
        }
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        return Transform.function("translate", words, context) instanceof Function.Translate translate
                ? new Parsed<>(translate)
                : null;
    }

    /// `rotate: <angle>`, or `z <angle>`, or `none`. Turning about any other
    /// axis is three-dimensional, which a box here is not.
    public static @Nullable Parsed<Function.Rotate> parseRotate(List<Token> value, CssLength.Context context) {
        var words = words(value);
        if (isNone(words)) {
            return new Parsed<>(null);
        }
        if (words.size() == 2
                && words.getFirst().size() == 1
                && words.getFirst().getFirst().isIdent("z")) {
            words = words.subList(1, 2);
        }
        if (words.size() != 1) {
            return null;
        }
        return Transform.function("rotate", words, context) instanceof Function.Rotate rotate
                ? new Parsed<>(rotate)
                : null;
    }

    /// `scale: x [y]`, numbers, one being both, or `none`.
    public static @Nullable Parsed<Function.Scale> parseScale(List<Token> value, CssLength.Context context) {
        var words = words(value);
        if (isNone(words)) {
            return new Parsed<>(null);
        }
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        return Transform.function("scale", words, context) instanceof Function.Scale scale ? new Parsed<>(scale) : null;
    }

    private static boolean isNone(List<List<Token>> words) {
        return words.size() == 1
                && words.getFirst().size() == 1
                && words.getFirst().getFirst().isIdent("none");
    }

    /// The value's words, split at the spaces outside a function.
    private static List<List<Token>> words(List<Token> value) {
        var words = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    words.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            words.add(List.copyOf(current));
        }
        return words;
    }
}
