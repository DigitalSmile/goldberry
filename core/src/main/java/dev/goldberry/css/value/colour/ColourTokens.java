package dev.goldberry.css.value.colour;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// Splitting a colour function's arguments at the commas and the spaces that
/// are not inside a nested function, which is what `color-mix()` and the
/// relative colour syntax both need before they can read a colour inside a
/// colour.
final class ColourTokens {

    private ColourTokens() {}

    /// The function's arguments: what is between its name and its `)`.
    static List<Token> inside(List<Token> function) {
        return function.subList(1, function.size() - 1);
    }

    /// Whether `tokens` are one function call, from its name to its `)`.
    static boolean isFunction(List<Token> tokens) {
        return tokens.size() >= 2
                && tokens.getFirst().is(TokenType.FUNCTION)
                && tokens.getLast().is(TokenType.CLOSE_PAREN);
    }

    /// `tokens` split at every comma outside a nested function, each part
    /// trimmed of whitespace.
    static List<List<Token>> commas(List<Token> tokens) {
        return split(tokens, true);
    }

    /// `tokens` split at every space outside a nested function, with any `/`
    /// its own part.
    static List<List<Token>> words(List<Token> tokens) {
        return split(tokens, false);
    }

    /// `tokens` without the whitespace at either end.
    static List<Token> trim(List<Token> tokens) {
        var from = 0;
        var to = tokens.size();
        while (from < to && tokens.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && tokens.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        return tokens.subList(from, to);
    }

    private static List<List<Token>> split(List<Token> tokens, boolean atCommas) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : tokens) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            var boundary = depth == 0
                    && (atCommas ? token.is(TokenType.COMMA) : token.is(TokenType.WHITESPACE) || token.isDelim('/'));
            if (!boundary) {
                current.add(token);
                continue;
            }
            if (atCommas) {
                parts.add(List.copyOf(trim(current)));
                current.clear();
                continue;
            }
            if (!current.isEmpty()) {
                parts.add(List.copyOf(current));
                current.clear();
            }
            if (token.isDelim('/')) {
                parts.add(List.of(token));
            }
        }
        if (atCommas) {
            parts.add(List.copyOf(trim(current)));
        } else if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }
}
