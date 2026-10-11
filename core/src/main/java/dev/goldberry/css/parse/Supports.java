package dev.goldberry.css.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.property.Properties;
import dev.goldberry.css.value.CssLength;

/// An `@supports` condition, answered once, when the sheet is parsed.
///
/// ```css
/// @supports (display: grid) { … }                 /* false: never applies */
/// @supports (aspect-ratio: 1) and selector(a:is(b)) { … }
/// ```
///
/// The question is about this engine, which does not change while a sheet is
/// loaded, so the answer is fixed then and the block is either read like any
/// other rules or skipped. A declaration test asks the engine itself, through
/// [ComputedStyle#applies]: a property it does not have, or a value it would
/// drop, is not supported, so an `@supports` block can never claim what a rule
/// outside it would not get. A `selector()` test parses the selector the way a
/// rule's would be parsed. `not`, `and` and `or` combine as CSS says, and
/// anything else in parentheses, `font-tech()` included, is false.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#media-queries).
final class Supports {

    /// What a declaration test resolves lengths against: a window, so a
    /// viewport unit is a length, as it is in a running application.
    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT.withViewport(1280, 800);

    private final List<Token> tokens;
    private int index;

    private Supports(List<Token> tokens) {
        this.tokens = tokens;
    }

    /// Whether `prelude`, the tokens between `@supports` and its `{`, holds.
    ///
    /// @throws IllegalArgumentException if it is not a condition at all
    static boolean holds(List<Token> prelude) {
        var parser = new Supports(prelude);
        var answer = parser.condition();
        parser.skipWhitespace();
        if (parser.index < parser.tokens.size()) {
            throw new IllegalArgumentException(
                    "unexpected " + parser.tokens.get(parser.index).describe());
        }
        return answer;
    }

    /// `not <in-parens>`, or `<in-parens>` joined by all `and` or all `or`.
    private boolean condition() {
        skipWhitespace();
        if (atIdent("not")) {
            index++;
            return !inParens();
        }
        var answer = inParens();
        String joiner = null;
        while (true) {
            skipWhitespace();
            if (index >= tokens.size() || !(atIdent("and") || atIdent("or"))) {
                return answer;
            }
            var word = tokens.get(index).text().toLowerCase(Locale.ROOT);
            if (joiner != null && !joiner.equals(word)) {
                throw new IllegalArgumentException("\"and\" and \"or\" may not be mixed without parentheses");
            }
            joiner = word;
            index++;
            var next = inParens();
            answer = word.equals("and") ? answer && next : answer || next;
        }
    }

    /// `( condition )`, `( property: value )`, `selector( … )`, or any other
    /// function or parenthesised text, which is false.
    private boolean inParens() {
        skipWhitespace();
        if (index >= tokens.size()) {
            throw new IllegalArgumentException("a condition ends where a test was expected");
        }
        var token = tokens.get(index);
        if (token.is(TokenType.FUNCTION)) {
            var inside = enclosed();
            return token.text().equalsIgnoreCase("selector") && selector(inside);
        }
        if (!token.is(TokenType.OPEN_PAREN)) {
            throw new IllegalArgumentException("expected \"(\", found " + token.describe());
        }
        var inside = enclosed();
        var trimmed = trim(inside);
        if (!trimmed.isEmpty()
                && (trimmed.getFirst().is(TokenType.OPEN_PAREN)
                        || trimmed.getFirst().is(TokenType.FUNCTION)
                        || trimmed.getFirst().isIdent("not"))) {
            var nested = new Supports(trimmed);
            var answer = nested.condition();
            nested.skipWhitespace();
            return nested.index == trimmed.size() && answer;
        }
        return declaration(trimmed);
    }

    /// The tokens between the `(` or function token at the parser and its
    /// `)`, which is consumed.
    private List<Token> enclosed() {
        index++;
        var depth = 1;
        var inside = new ArrayList<Token>();
        while (index < tokens.size()) {
            var token = tokens.get(index++);
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN) && --depth == 0) {
                return inside;
            }
            inside.add(token);
        }
        throw new IllegalArgumentException("an unclosed \"(\"");
    }

    /// `property: value`: whether this engine reads the property and would
    /// apply the value. A custom property is always supported.
    private static boolean declaration(List<Token> tokens) {
        if (tokens.size() < 2 || !tokens.getFirst().is(TokenType.IDENT)) {
            return false;
        }
        var name = tokens.getFirst().text();
        var colon = 1;
        while (colon < tokens.size() && tokens.get(colon).is(TokenType.WHITESPACE)) {
            colon++;
        }
        if (colon >= tokens.size() || !tokens.get(colon).is(TokenType.COLON)) {
            return false;
        }
        var value = trim(tokens.subList(colon + 1, tokens.size()));
        if (name.startsWith("--")) {
            return true;
        }
        if (value.isEmpty()) {
            return false;
        }
        var property = name.toLowerCase(Locale.ROOT);
        return Properties.find(property).isPresent() && ComputedStyle.applies(property, value, CONTEXT);
    }

    /// Whether `tokens` are one selector this subset reads.
    private static boolean selector(List<Token> tokens) {
        var text = new StringBuilder();
        for (var token : tokens) {
            text.append(token.cssText());
        }
        try {
            CssParser.parseSheet(text + " {}", ParseMode.STRICT, "an @supports selector()");
            return !trim(tokens).isEmpty();
        } catch (CssSyntaxException _) {
            return false;
        }
    }

    private static List<Token> trim(List<Token> tokens) {
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

    private void skipWhitespace() {
        while (index < tokens.size() && tokens.get(index).is(TokenType.WHITESPACE)) {
            index++;
        }
    }

    private boolean atIdent(String word) {
        return index < tokens.size() && tokens.get(index).isIdent(word);
    }
}
