package dev.goldberry.css.value.colour;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// `currentcolor`: the element's own `color`, wherever a colour is written.
///
/// Resolved by substitution before a value is read. Every `currentcolor` in it
/// becomes the colour as a hex token, so a border, a shadow, a gradient stop or
/// an outline reads it as it reads any other colour, with no parser of its own
/// knowing the keyword.
///
/// The value is fixed when the style is computed. A `color` transition moves
/// the text and not a border that said `currentcolor`, which keeps the colour
/// it had when the transition's target style was computed.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class CurrentColour {

    private CurrentColour() {}

    /// `value` with every `currentcolor` replaced by `argb`, or `value` itself
    /// when it has none, which is nearly always.
    public static List<Token> substitute(List<Token> value, int argb) {
        var found = false;
        for (var token : value) {
            if (token.isIdent("currentcolor")) {
                found = true;
                break;
            }
        }
        if (!found) {
            return value;
        }
        var substituted = new ArrayList<Token>(value.size());
        for (var token : value) {
            substituted.add(token.isIdent("currentcolor") ? hex(argb, token) : token);
        }
        return List.copyOf(substituted);
    }

    /// `argb` as the token `#rrggbbaa` would tokenize to, at `at`'s place.
    private static Token hex(int argb, Token at) {
        var rgba = String.format("%06x%02x", argb & 0xFFFFFF, argb >>> 24);
        return new Token(TokenType.HASH, rgba, 0, "", at.line(), at.column());
    }
}
