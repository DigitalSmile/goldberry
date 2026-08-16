package io.github.digitalsmile.goldberry.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Turns [Token]s into [StyleRule]s.
///
/// Parses the subset in `ARCHITECTURE.md` §8 and refuses everything else, rather
/// than following the CSS spec's rule of discarding what it does not understand.
/// A browser must render a page written for browsers it has never heard of; a
/// toolkit is reading a stylesheet its own application shipped, and a silently
/// dropped rule there is a widget that is the wrong colour with nothing in the
/// log — see [CssSyntaxException].
///
/// At-rules are recognised but not yet applied: `@media` is parsed for its block
/// so the rules inside are not lost, and the condition is retained on the rules
/// it produced. Evaluating those conditions needs a window to ask about width and
/// colour scheme, which is the next piece.
public final class CssParser {

    private final List<Token> tokens;
    private int index;
    private int ruleOrder;

    private CssParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /// Parses a stylesheet's text.
    ///
    /// @throws CssSyntaxException if anything in it is not in the supported subset
    public static List<StyleRule> parse(String css) {
        Objects.requireNonNull(css, "css");
        return new CssParser(CssTokenizer.tokenize(css)).parseRules();
    }

    private List<StyleRule> parseRules() {
        var rules = new ArrayList<StyleRule>();
        skipWhitespace();
        while (!peek().is(TokenType.EOF)) {
            if (peek().is(TokenType.AT_KEYWORD)) {
                rules.addAll(atRule());
            } else {
                rules.add(styleRule());
            }
            skipWhitespace();
        }
        return List.copyOf(rules);
    }

    /// `@media (...) { ... }`.
    ///
    /// The condition is consumed and, for now, discarded: nothing evaluates it
    /// yet. The rules inside are kept, which is deliberately the *permissive*
    /// choice — a themed stylesheet whose dark-mode block silently vanished
    /// would be far harder to diagnose than one that applies too eagerly, and
    /// both are wrong only until the media evaluator lands.
    private List<StyleRule> atRule() {
        var at = advance();
        if (!at.text().equalsIgnoreCase("media")) {
            throw error(at, "unsupported at-rule \"@" + at.text()
                    + "\"; this subset has @media and nothing else");
        }
        // The prelude, up to the block.
        while (!peek().is(TokenType.OPEN_BRACE)) {
            if (peek().is(TokenType.EOF)) {
                throw error(peek(), "@media has no block");
            }
            advance();
        }
        advance(); // {

        var inner = new ArrayList<StyleRule>();
        skipWhitespace();
        while (!peek().is(TokenType.CLOSE_BRACE)) {
            if (peek().is(TokenType.EOF)) {
                throw error(peek(), "unclosed @media block");
            }
            inner.add(styleRule());
            skipWhitespace();
        }
        advance(); // }
        return inner;
    }

    private StyleRule styleRule() {
        var selectors = selectorList();
        var declarations = declarationBlock();
        return new StyleRule(selectors, declarations, ruleOrder++);
    }

    /// `.a, .b > c` — up to the `{`.
    private List<Selector> selectorList() {
        var selectors = new ArrayList<Selector>();
        while (true) {
            selectors.add(selector());
            skipWhitespace();
            if (peek().is(TokenType.COMMA)) {
                advance();
                skipWhitespace();
                continue;
            }
            if (peek().is(TokenType.OPEN_BRACE)) {
                return selectors;
            }
            throw error(peek(), "expected \",\" or \"{\" after a selector, found " + peek().describe());
        }
    }

    /// One selector, returned rightmost-compound-first.
    private Selector selector() {
        // Built left to right as written, then reversed: matching wants the key
        // compound first (see Selector), and reversing once here is cheaper than
        // every matcher indexing backwards.
        var compounds = new ArrayList<Selector.Compound>();
        var combinators = new ArrayList<Selector.Combinator>();

        skipWhitespace();
        compounds.add(compound());

        while (true) {
            var sawWhitespace = skipWhitespace();
            if (peek().isDelim('>')) {
                advance();
                skipWhitespace();
                combinators.add(Selector.Combinator.CHILD);
                compounds.add(compound());
                continue;
            }
            // Whitespace is only a combinator when something selectable follows;
            // the space in ".a { " is just spacing.
            if (sawWhitespace && startsCompound()) {
                combinators.add(Selector.Combinator.DESCENDANT);
                compounds.add(compound());
                continue;
            }
            break;
        }

        // Written left to right as `compounds[0] combinators[0] compounds[1] ...`,
        // so combinators[i] joins compounds[i] to compounds[i + 1].
        //
        // Wanted: rightmost first, each part carrying the combinator that joins
        // it to the compound on its LEFT. Part j is compounds[n-1-j], and the
        // combinator to its left is combinators[n-2-j] -- except the leftmost
        // compound, which has nothing before it.
        var n = compounds.size();
        var parts = new ArrayList<Selector.Part>(n);
        for (var j = 0; j < n; j++) {
            var combinator = j < n - 1
                    ? combinators.get(n - 2 - j)
                    : Selector.Combinator.NONE;
            parts.add(new Selector.Part(compounds.get(n - 1 - j), combinator));
        }
        return new Selector(parts);
    }

    private boolean startsCompound() {
        var token = peek();
        return token.is(TokenType.IDENT)
                || token.is(TokenType.HASH)
                || token.is(TokenType.COLON)
                || token.isDelim('.')
                || token.isDelim('*');
    }

    /// `button.primary:hover`, with no whitespace inside it.
    private Selector.Compound compound() {
        String type = null;
        String id = null;
        var classes = new ArrayList<String>();
        var pseudoClasses = new ArrayList<Selector.PseudoClass>();
        var start = peek();
        var sawAnything = false;

        while (true) {
            var token = peek();
            if (token.is(TokenType.IDENT) && !sawAnything) {
                // A type only counts first: "a b" is two compounds, and "a.b c"
                // has the type on the first.
                type = advance().text().toLowerCase(Locale.ROOT);
                sawAnything = true;
            } else if (token.isDelim('*') && !sawAnything) {
                advance();
                sawAnything = true;
            } else if (token.is(TokenType.HASH)) {
                if (!token.isIdentifierLike()) {
                    throw error(token, "\"#" + token.text() + "\" is not a valid id");
                }
                if (id != null) {
                    throw error(token, "a compound selector may name only one id");
                }
                id = advance().text();
                sawAnything = true;
            } else if (token.isDelim('.')) {
                advance();
                if (!peek().is(TokenType.IDENT)) {
                    throw error(peek(), "expected a class name after \".\", found " + peek().describe());
                }
                classes.add(advance().text());
                sawAnything = true;
            } else if (token.is(TokenType.COLON)) {
                advance();
                if (peek().is(TokenType.COLON)) {
                    throw error(token, "pseudo-elements (::) are not in this subset");
                }
                if (!peek().is(TokenType.IDENT)) {
                    throw error(peek(), "expected a pseudo-class name after \":\", found "
                            + peek().describe());
                }
                var name = advance();
                var pseudo = Selector.PseudoClass.parse(name.text());
                if (pseudo == null) {
                    // Named rather than ignored: ":hovered" as a silently
                    // never-matching rule is a bad afternoon.
                    throw error(name, "unknown pseudo-class \":" + name.text() + "\"; supported: "
                            + supportedPseudoClasses());
                }
                pseudoClasses.add(pseudo);
                sawAnything = true;
            } else {
                break;
            }
        }

        if (!sawAnything) {
            throw error(start, "expected a selector, found " + start.describe());
        }
        return new Selector.Compound(type, id, classes, pseudoClasses);
    }

    private static String supportedPseudoClasses() {
        var names = new ArrayList<String>();
        for (var value : Selector.PseudoClass.values()) {
            names.add(":" + value.cssName());
        }
        return String.join(" ", names);
    }

    /// `{ color: red; padding: 4px }`.
    private List<Declaration> declarationBlock() {
        expect(TokenType.OPEN_BRACE, "{");
        var declarations = new ArrayList<Declaration>();
        while (true) {
            skipWhitespace();
            if (peek().is(TokenType.CLOSE_BRACE)) {
                advance();
                return declarations;
            }
            if (peek().is(TokenType.EOF)) {
                throw error(peek(), "unclosed declaration block");
            }
            if (peek().is(TokenType.SEMICOLON)) {
                // An empty declaration. Legal, and means nothing.
                advance();
                continue;
            }
            declarations.add(declaration());
        }
    }

    private Declaration declaration() {
        var name = peek();
        if (!name.is(TokenType.IDENT)) {
            throw error(name, "expected a property name, found " + name.describe());
        }
        advance();
        // Custom properties keep their case: "--gbAccent" and "--gbaccent" are
        // different properties. Everything else is a known keyword and is not.
        var property = name.text().startsWith("--")
                ? name.text()
                : name.text().toLowerCase(Locale.ROOT);

        skipWhitespace();
        expect(TokenType.COLON, ":");

        var value = new ArrayList<Token>();
        var depth = 0;
        while (true) {
            var token = peek();
            if (token.is(TokenType.EOF)) {
                throw error(token, "declaration \"" + property + "\" has no closing \";\" or \"}\"");
            }
            // A "}" inside var(...) or a media condition is not the end of the
            // block, so nesting has to be tracked rather than assumed.
            if (depth == 0 && (token.is(TokenType.SEMICOLON) || token.is(TokenType.CLOSE_BRACE))) {
                break;
            }
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
            }
            value.add(advance());
        }
        if (peek().is(TokenType.SEMICOLON)) {
            advance();
        }

        var important = false;
        var trimmed = trimWhitespace(value);
        if (endsWithImportant(trimmed)) {
            important = true;
            trimmed = trimWhitespace(trimmed.subList(0, trimmed.size() - 2));
        }
        if (trimmed.isEmpty()) {
            throw error(name, "declaration \"" + property + "\" has no value");
        }
        return new Declaration(property, trimmed, important, name.line(), name.column());
    }

    /// `! important`, with the whitespace between them already removed.
    private static boolean endsWithImportant(List<Token> value) {
        if (value.size() < 2) {
            return false;
        }
        var last = value.get(value.size() - 1);
        var bang = value.get(value.size() - 2);
        return bang.isDelim('!') && last.isIdent("important");
    }

    private static List<Token> trimWhitespace(List<Token> value) {
        var from = 0;
        var to = value.size();
        while (from < to && value.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && value.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        return value.subList(from, to);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token advance() {
        var token = tokens.get(index);
        if (!token.is(TokenType.EOF)) {
            index++;
        }
        return token;
    }

    /// @return whether any whitespace was skipped, which in a selector is the
    ///         difference between a descendant combinator and nothing at all
    private boolean skipWhitespace() {
        var skipped = false;
        while (peek().is(TokenType.WHITESPACE)) {
            advance();
            skipped = true;
        }
        return skipped;
    }

    private void expect(TokenType type, String what) {
        if (!peek().is(type)) {
            throw error(peek(), "expected \"" + what + "\", found " + peek().describe());
        }
        advance();
    }

    private static CssSyntaxException error(Token at, String message) {
        return new CssSyntaxException(message, at.line(), at.column());
    }
}
