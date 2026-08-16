package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The tokenizer, against the parts of CSS that are easy to get subtly wrong.
///
/// Most of these are cases where a hand-rolled scanner and the spec disagree, and
/// where the disagreement does not show up until somebody's stylesheet behaves
/// oddly: `#ff0000` is not an identifier, `- 5px` is not a number, `var (x)` is
/// not a function call.
class CssTokenizerTest {

    /// Tokens with the whitespace dropped, which is what most assertions here
    /// care about. Whitespace has its own tests, because in a selector it is a
    /// combinator and must survive.
    private static List<Token> significant(String css) {
        return CssTokenizer.tokenize(css).stream()
                .filter(t -> !t.is(TokenType.WHITESPACE) && !t.is(TokenType.EOF))
                .toList();
    }

    private static Token only(String css) {
        var tokens = significant(css);
        assertEquals(1, tokens.size(), () -> "expected one token, got " + tokens);
        return tokens.getFirst();
    }

    @Test
    @DisplayName("the last token is always EOF, so a parser can look ahead safely")
    void alwaysEndsWithEof() {
        assertTrue(CssTokenizer.tokenize("").getLast().is(TokenType.EOF));
        assertTrue(CssTokenizer.tokenize("a{}").getLast().is(TokenType.EOF));
    }

    @Nested
    @DisplayName("identifiers")
    class Identifiers {

        @ParameterizedTest
        @ValueSource(strings = {"div", "flex-start", "_private", "a1", "-webkit-thing"})
        @DisplayName("ordinary names")
        void names(String name) {
            var token = only(name);
            assertTrue(token.is(TokenType.IDENT));
            assertEquals(name, token.text());
        }

        @Test
        @DisplayName("a custom property is an ident, two hyphens and all")
        void customPropertiesAreIdents() {
            // The whole theming mechanism (ARCHITECTURE.md §8) rests on this
            // being a normal identifier rather than a special case.
            var token = only("--gb-bg-subtle");
            assertTrue(token.is(TokenType.IDENT));
            assertEquals("--gb-bg-subtle", token.text());
        }

        @Test
        @DisplayName("a non-ASCII name is an identifier")
        void nonAsciiNames() {
            // A class named in a language that is not English has to work.
            var tokens = significant(".größe");
            assertTrue(tokens.get(0).isDelim('.'));
            assertEquals("größe", tokens.get(1).text());
            assertTrue(tokens.get(1).is(TokenType.IDENT));
        }

        @Test
        @DisplayName("a lone hyphen is a delim, not a name")
        void loneHyphen() {
            assertTrue(only("-").isDelim('-'));
        }

        @Test
        @DisplayName("an ident followed by ( is a function; with a space it is not")
        void functionsNeedTheParenAdjacent() {
            var call = only("var(");
            assertTrue(call.is(TokenType.FUNCTION));
            assertEquals("var", call.text());

            // "var (x)" is an ident, whitespace, then a paren -- three tokens.
            var spaced = significant("var (");
            assertEquals(2, spaced.size());
            assertTrue(spaced.get(0).is(TokenType.IDENT));
            assertTrue(spaced.get(1).is(TokenType.OPEN_PAREN));
        }
    }

    @Nested
    @DisplayName("hashes")
    class Hashes {

        @Test
        @DisplayName("#main is identifier-like, so it can be an id selector")
        void idSelector() {
            var token = only("#main");
            assertTrue(token.is(TokenType.HASH));
            assertEquals("main", token.text());
            assertTrue(token.isIdentifierLike());
        }

        @Test
        @DisplayName("#123456 is a hash that is NOT identifier-like")
        void numericHexColour() {
            // An identifier cannot start with a digit, so this colour can never
            // be mistaken for an id selector.
            var token = only("#123456");
            assertTrue(token.is(TokenType.HASH));
            assertFalse(token.isIdentifierLike());
        }

        @Test
        @DisplayName("#ff0000 IS identifier-like, because it is also a legal name")
        void ambiguousHash() {
            // Worth pinning because it is counter-intuitive: "ff0000" starts
            // with a letter and contains only name characters, so it is a valid
            // identifier *and* a valid colour. The spec does not resolve that in
            // the tokenizer, and neither does this -- a hash in a selector is an
            // id, a hash in a value is a colour, and position is what decides.
            assertTrue(only("#ff0000").isIdentifierLike());
            assertTrue(only("#abcdef").isIdentifierLike());
        }

        @Test
        @DisplayName("a bare # is a delim")
        void bareHash() {
            assertTrue(only("# ").isDelim('#'));
        }
    }

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @ParameterizedTest
        @CsvSource({"1, 1", "'-2.5', -2.5", "'+3', 3", "'.5', 0.5", "'1e3', 1000", "'2E-2', 0.02"})
        @DisplayName("numeric forms")
        void numbers(String css, double expected) {
            var token = only(css);
            assertTrue(token.is(TokenType.NUMBER), () -> css + " -> " + token);
            assertEquals(expected, token.numeric(), 1e-9);
        }

        @Test
        @DisplayName("a dimension keeps its unit, lowercased")
        void dimensions() {
            var token = only("16PX");
            assertTrue(token.is(TokenType.DIMENSION));
            assertEquals(16, token.numeric(), 1e-9);
            assertEquals("px", token.unit());
        }

        @Test
        @DisplayName("1em is a dimension but 1e3 is a number")
        void exponentVersusUnit() {
            // The one place a lookahead is genuinely required: "e" starts a unit
            // unless digits follow it.
            assertTrue(only("1em").is(TokenType.DIMENSION));
            assertEquals("em", only("1em").unit());
            assertTrue(only("1e3").is(TokenType.NUMBER));
        }

        @Test
        @DisplayName("a percentage keeps the number before the sign")
        void percentages() {
            var token = only("50%");
            assertTrue(token.is(TokenType.PERCENTAGE));
            // 50, not 0.5 -- dividing here would make "50%" and "0.5" the same
            // token, and they mean different things to a layout engine.
            assertEquals(50, token.numeric(), 1e-9);
        }

        @Test
        @DisplayName("-5px is one token; - 5px is three")
        void signIsPartOfTheNumber() {
            assertTrue(only("-5px").is(TokenType.DIMENSION));
            assertEquals(-5, only("-5px").numeric(), 1e-9);

            var spaced = significant("- 5px");
            assertEquals(2, spaced.size());
            assertTrue(spaced.get(0).isDelim('-'));
            assertTrue(spaced.get(1).is(TokenType.DIMENSION));
        }
    }

    @Nested
    @DisplayName("strings")
    class Strings {

        @Test
        @DisplayName("quotes are removed and either kind works")
        void quoted() {
            assertEquals("Inter", only("\"Inter\"").text());
            assertEquals("Inter", only("'Inter'").text());
            assertTrue(only("\"Inter\"").is(TokenType.STRING));
        }

        @Test
        @DisplayName("an escaped quote does not end the string")
        void escapedQuote() {
            assertEquals("a\"b", only("\"a\\\"b\"").text());
        }

        @Test
        @DisplayName("a backslash-newline inside a string is a line continuation")
        void lineContinuation() {
            assertEquals("ab", only("\"a\\\nb\"").text());
        }

        @Test
        @DisplayName("an unterminated string is refused, with the position it opened at")
        void unterminated() {
            var thrown = assertThrows(CssSyntaxException.class, () -> CssTokenizer.tokenize("a { content: \"oops"));
            assertEquals(1, thrown.line());
            assertEquals(14, thrown.column());
        }

        @Test
        @DisplayName("a newline inside a string is refused rather than recovered")
        void newlineInString() {
            // The spec emits a bad-string and carries on. Guessing where the
            // quote was meant to close is how one typo silently eats the next
            // ten rules -- see CssSyntaxException.
            assertThrows(CssSyntaxException.class, () -> CssTokenizer.tokenize("a { content: \"oops\n\" }"));
        }
    }

    @Nested
    @DisplayName("escapes")
    class Escapes {

        @Test
        @DisplayName("a hex escape becomes its character")
        void hexEscape() {
            assertEquals("A", only("\\41 ").text());
        }

        @Test
        @DisplayName("one whitespace after a hex escape is eaten, and only one")
        void hexEscapeEatsOneSpace() {
            // "\\41 B" is "AB": the space terminates the hex digits rather than
            // separating two tokens. Getting this wrong turns one class name
            // into two.
            assertEquals("AB", only("\\41 B").text());
        }

        @Test
        @DisplayName("a non-hex escape is the literal character")
        void literalEscape() {
            assertEquals("a.b", only("a\\.b").text());
        }

        @Test
        @DisplayName("a null or surrogate escape becomes the replacement character")
        void outOfRangeEscape() {
            assertEquals("�", only("\\0 ").text());
            assertEquals("�", only("\\D800 ").text());
        }
    }

    @Nested
    @DisplayName("whitespace and comments")
    class WhitespaceAndComments {

        @Test
        @DisplayName("whitespace survives, because in a selector it is a combinator")
        void whitespaceIsAToken() {
            var tokens = CssTokenizer.tokenize(".a .b");
            // ".a .b" (descendant) and ".a.b" (both classes) must not tokenize
            // to the same thing.
            assertTrue(tokens.stream().anyMatch(t -> t.is(TokenType.WHITESPACE)));
            assertFalse(CssTokenizer.tokenize(".a.b").stream().anyMatch(t -> t.is(TokenType.WHITESPACE)));
        }

        @Test
        @DisplayName("a run of whitespace collapses to one token")
        void whitespaceCollapses() {
            assertEquals(1, CssTokenizer.tokenize("  \n\t ").stream()
                    .filter(t -> t.is(TokenType.WHITESPACE)).count());
        }

        @Test
        @DisplayName("comments vanish entirely, even back to back")
        void commentsAreDropped() {
            var joined = significant("a/* one *//* two */b");
            assertEquals(2, joined.size());
            assertEquals("a", joined.get(0).text());
            assertEquals("b", joined.get(1).text());
        }

        @Test
        @DisplayName("a comment does not join the tokens either side of it")
        void commentsDoNotFuseTokens() {
            // "a/* x */b" is two idents. A tokenizer that stripped comments with
            // a string replace before scanning would produce one ident "ab".
            var tokens = significant("a/* x */b");
            assertEquals(2, tokens.size());
            assertFalse(tokens.stream().anyMatch(t -> t.text().equals("ab")));
        }

        @Test
        @DisplayName("an unterminated comment is refused")
        void unterminatedComment() {
            assertThrows(CssSyntaxException.class, () -> CssTokenizer.tokenize("a { } /* oops"));
        }
    }

    @Nested
    @DisplayName("source positions")
    class Positions {

        @Test
        @DisplayName("line and column point at the token's first character")
        void positionsAreReported() {
            var tokens = significant(".a {\n  color: red;\n}");
            var color = tokens.stream().filter(t -> t.isIdent("color")).findFirst().orElseThrow();
            assertEquals(2, color.line());
            assertEquals(3, color.column());
        }

        @Test
        @DisplayName("CRLF counts as one newline, so columns do not drift")
        void crlfIsOneNewline() {
            var tokens = significant("a\r\nb");
            assertEquals(2, tokens.get(1).line());
            assertEquals(1, tokens.get(1).column());
        }
    }

    @Nested
    @DisplayName("a whole rule")
    class WholeRule {

        @Test
        @DisplayName("a realistic rule tokenizes to what the parser will expect")
        void realisticRule() {
            var tokens = significant(".button:hover > .icon { color: var(--gb-accent); padding: 4px 8px }");

            var types = tokens.stream().map(Token::type).toList();
            assertEquals(List.of(
                    TokenType.DELIM,        // .
                    TokenType.IDENT,        // button
                    TokenType.COLON,
                    TokenType.IDENT,        // hover
                    TokenType.DELIM,        // >
                    TokenType.DELIM,        // .
                    TokenType.IDENT,        // icon
                    TokenType.OPEN_BRACE,
                    TokenType.IDENT,        // color
                    TokenType.COLON,
                    TokenType.FUNCTION,     // var(
                    TokenType.IDENT,        // --gb-accent
                    TokenType.CLOSE_PAREN,
                    TokenType.SEMICOLON,
                    TokenType.IDENT,        // padding
                    TokenType.COLON,
                    TokenType.DIMENSION,    // 4px
                    TokenType.DIMENSION,    // 8px
                    TokenType.CLOSE_BRACE), types);
        }

        @Test
        @DisplayName("an at-rule keeps its name without the @")
        void atRule() {
            var token = significant("@media (min-width: 600px) {}").getFirst();
            assertTrue(token.is(TokenType.AT_KEYWORD));
            assertEquals("media", token.text());
        }
    }
}
