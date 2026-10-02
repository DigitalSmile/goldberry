package dev.goldberry.css.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.Keyframes;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.media.MediaQueries;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.select.Structural;
import dev.goldberry.log.Logs;

/// Turns [Token]s into [StyleRule]s.
///
/// ```java
/// CssParser.Parsed parsed = CssParser.parseSheet(css);
/// ```
///
/// Parses the supported subset, and does one of two things with what lies
/// outside it, by [ParseMode]. [ParseMode#STRICT], the default here and what the
/// toolkit's own sheets are read under, refuses the sheet with a
/// [CssSyntaxException]: a rule in `controls.css` that silently matched nothing
/// would be a control drawn wrong everywhere. [ParseMode#LENIENT], what an
/// application's sheets are read under, drops the one rule that asked for it,
/// warns once with the selector, the sheet and the line, and records a
/// [DroppedRule]. A malformed sheet is refused in both.
///
/// Three at-rules are read. `@media` gives every rule inside it the block's
/// [MediaCondition], which the cascade evaluates against the window; a feature
/// the toolkit cannot answer makes the block never apply, with a warning.
/// `@starting-style` marks the rules inside it as the style an element
/// transitions *from* on its first frame. `@keyframes` names a sequence
/// `animation-name` can run.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
public final class CssParser {

    private static final Logger LOG = Logs.of(CssParser.class);

    /// What a stylesheet's text parsed into: its rules, its named keyframes, and
    /// what a lenient parse left out.
    ///
    /// @param rules     in source order, `@starting-style` and `@media` rules
    ///                  among them
    /// @param keyframes in source order; a later block with the same name wins,
    ///                  which is the cascade's business rather than the parser's
    /// @param dropped   the rules a [ParseMode#LENIENT] parse dropped, and the
    ///                  `@media` blocks it will never apply; always empty under
    ///                  [ParseMode#STRICT], which throws instead
    public record Parsed(List<StyleRule> rules, List<Keyframes> keyframes, List<DroppedRule> dropped) {

        public Parsed {
            rules = List.copyOf(rules);
            keyframes = List.copyOf(keyframes);
            dropped = List.copyOf(dropped);
        }

        /// A parse that dropped nothing.
        public Parsed(List<StyleRule> rules, List<Keyframes> keyframes) {
            this(rules, keyframes, List.of());
        }
    }

    /// What a sheet is called in a warning when its caller gave it no name.
    private static final String UNNAMED = "a stylesheet";

    private final List<Token> tokens;
    private final ParseMode mode;
    private final String origin;
    private int index;
    private int ruleOrder;
    private final List<Keyframes> keyframes = new ArrayList<>();
    private final List<DroppedRule> dropped = new ArrayList<>();

    private CssParser(List<Token> tokens, ParseMode mode, String origin) {
        this.tokens = tokens;
        this.mode = mode;
        this.origin = origin;
    }

    /// Parses a stylesheet's text, strictly.
    ///
    /// @throws CssSyntaxException if anything in it is not in the supported subset
    public static List<StyleRule> parse(String css) {
        return parseSheet(css).rules();
    }

    /// Parses a stylesheet's text into its rules **and** its keyframes, strictly.
    ///
    /// @throws CssSyntaxException if anything in it is not in the supported subset
    public static Parsed parseSheet(String css) {
        return parseSheet(css, ParseMode.STRICT, UNNAMED);
    }

    /// Parses a stylesheet's text under `mode`.
    ///
    /// @param origin what the sheet is called in a warning: its resource name,
    ///               usually
    /// @throws CssSyntaxException if the text is malformed, or under
    ///         [ParseMode#STRICT] if anything in it is not in the subset
    public static Parsed parseSheet(String css, ParseMode mode, String origin) {
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(origin, "origin");
        var parser = new CssParser(CssTokenizer.tokenize(css), mode, origin);
        var rules = new ArrayList<StyleRule>();
        parser.rules(rules, MediaCondition.ALWAYS, false, false);
        return new Parsed(rules, parser.keyframes, parser.dropped);
    }

    /// Rules up to the end of the sheet or, inside a block, up to its `}`, which
    /// is left for the caller.
    ///
    /// The one place a lenient parse recovers. A rule that asks for something
    /// outside the subset is re-read from its start and skipped whole, through
    /// the end of its block; anything else that goes wrong is thrown, because a
    /// sheet that is malformed cannot be skipped through reliably.
    ///
    /// @param media    the condition every rule here applies under
    /// @param starting whether these are `@starting-style` rules
    /// @param nested   whether this is a block's body, ended by `}`
    private void rules(List<StyleRule> into, MediaCondition media, boolean starting, boolean nested) {
        while (true) {
            skipWhitespace();
            var next = peek();
            if (next.is(TokenType.EOF)) {
                if (nested) {
                    throw error(next, "unclosed block");
                }
                return;
            }
            if (nested && next.is(TokenType.CLOSE_BRACE)) {
                return;
            }
            var start = index;
            try {
                if (next.is(TokenType.AT_KEYWORD)) {
                    if (starting) {
                        throw error(next, "@starting-style holds style rules and nothing else");
                    }
                    atRule(into, media);
                } else {
                    into.add(styleRule(media, starting));
                }
            } catch (CssSyntaxException e) {
                if (mode == ParseMode.STRICT || !e.isUnsupportedFeature()) {
                    throw e;
                }
                index = start;
                var at = peek();
                var text = skipRule();
                drop(text, reason(e), at);
            }
        }
    }

    /// `@media`, `@starting-style` or `@keyframes`, under `media`.
    private void atRule(List<StyleRule> into, MediaCondition media) {
        var at = advance();
        var name = at.text().toLowerCase(Locale.ROOT);
        switch (name) {
            case "starting-style" -> startingStyle(at, into, media);
            case "keyframes" -> {
                if (media != MediaCondition.ALWAYS) {
                    throw unsupported(
                            at, "@keyframes inside @media is not in this subset; declare it at the top level");
                }
                keyframes.add(keyframes(at));
            }
            case "media" -> media(at, into, media);
            default ->
                throw unsupported(
                        at,
                        "unsupported at-rule \"@" + at.text()
                                + "\"; this subset has @media, @starting-style and @keyframes and nothing else");
        }
    }

    /// `@media <queries> { rules }`.
    ///
    /// Every rule inside carries the block's condition, joined with the
    /// enclosing block's when they nest, and the cascade decides per frame
    /// whether it applies. A query naming a feature the toolkit cannot answer
    /// never applies: refused under [ParseMode#STRICT], and under
    /// [ParseMode#LENIENT] kept, never matched, and warned about once.
    private void media(Token at, List<StyleRule> into, MediaCondition enclosing) {
        var prelude = new ArrayList<Token>();
        while (!peek().is(TokenType.OPEN_BRACE)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.SEMICOLON)) {
                throw error(peek(), "@media has no block");
            }
            prelude.add(advance());
        }
        var condition = MediaQueries.parse(prelude);
        var reason = MediaCondition.unsupported(condition);
        if (reason.isPresent()) {
            if (mode == ParseMode.STRICT) {
                throw unsupported(at, "@media " + condition + " cannot be evaluated: " + reason.get());
            }
            // A list keeps its readable queries, which is CSS's rule, so only a
            // block whose every query is unreadable never applies at all.
            var whole = condition instanceof MediaCondition.Unsupported;
            drop(
                    "@media " + condition,
                    (whole ? "never applies: " : "a query in it never applies: ") + reason.get(),
                    at);
        }
        advance(); // {
        var combined =
                enclosing != MediaCondition.ALWAYS ? new MediaCondition.And(List.of(enclosing, condition)) : condition;
        rules(into, combined, false, true);
        advance(); // }
    }

    /// `@starting-style { rules }` — the block form, which is the one CSS has
    /// at the top level of a sheet.
    ///
    /// The rules inside are ordinary rules marked as starting styles. They keep
    /// their place in the source order, so a starting rule written after a normal
    /// one of equal specificity wins against it, as CSS says.
    private void startingStyle(Token at, List<StyleRule> into, MediaCondition media) {
        skipWhitespace();
        if (!peek().is(TokenType.OPEN_BRACE)) {
            throw error(at, "@starting-style takes a block and no prelude; found " + peek().describe());
        }
        advance(); // {
        rules(into, media, true, true);
        advance(); // }
    }

    /// Skips the rule starting at the current token, through the end of its
    /// block or its `;`, and returns its prelude as written.
    private String skipRule() {
        var prelude = new StringBuilder();
        var depth = 0;
        while (true) {
            var token = peek();
            if (token.is(TokenType.EOF)) {
                throw error(token, "unclosed rule");
            }
            if (depth == 0 && token.is(TokenType.SEMICOLON)) {
                advance();
                return squeeze(prelude);
            }
            if (depth == 0 && token.is(TokenType.OPEN_BRACE)) {
                break;
            }
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN) || token.is(TokenType.OPEN_BRACKET)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN) || token.is(TokenType.CLOSE_BRACKET)) {
                depth--;
            }
            prelude.append(advance().cssText());
        }
        var braces = 0;
        while (true) {
            var token = advance();
            if (token.is(TokenType.EOF)) {
                throw error(token, "unclosed block");
            }
            if (token.is(TokenType.OPEN_BRACE)) {
                braces++;
            } else if (token.is(TokenType.CLOSE_BRACE) && --braces == 0) {
                return squeeze(prelude);
            }
        }
    }

    private static String squeeze(CharSequence text) {
        return text.toString().trim().replaceAll("\\s+", " ");
    }

    /// An error's message without the position [CssSyntaxException] appends,
    /// which a dropped rule carries separately.
    private static String reason(CssSyntaxException e) {
        var message = Objects.requireNonNullElse(e.getMessage(), "outside the subset");
        var position = message.lastIndexOf(" (line ");
        return position < 0 ? message : message.substring(0, position);
    }

    /// Records a dropped rule and says so, once, at warn.
    private void drop(String text, String reason, Token at) {
        dropped.add(new DroppedRule(text, reason, at.line(), at.column()));
        LOG.warn("{}, line {}: dropping \"{}\": {}", origin, at.line(), text, reason);
    }

    /// `@keyframes name { from { … } 50%, 75% { … } to { … } }`.
    ///
    /// Refused rather than tolerated for the reasons the rest of this parser
    /// refuses: a keyframe selector that is not `from`, `to` or a percentage from
    /// 0 to 100, an empty name, or `!important` inside a keyframe, which CSS
    /// ignores and an author who wrote it did not expect to be ignored.
    private Keyframes keyframes(Token at) {
        skipWhitespace();
        var name = peek();
        if (!(name.is(TokenType.IDENT) || name.is(TokenType.STRING)) || name.isIdent("none")) {
            throw error(name, "@keyframes needs a name, and " + name.describe() + " is not one");
        }
        advance();
        skipWhitespace();
        if (!peek().is(TokenType.OPEN_BRACE)) {
            throw error(peek(), "expected \"{\" after @keyframes " + name.text() + ", found " + peek().describe());
        }
        advance(); // {
        var frames = new ArrayList<Keyframes.Frame>();
        skipWhitespace();
        while (!peek().is(TokenType.CLOSE_BRACE)) {
            if (peek().is(TokenType.EOF)) {
                throw error(peek(), "unclosed @keyframes " + name.text());
            }
            var offsets = keyframeSelectors();
            var declarations = declarationBlock();
            for (var declaration : declarations) {
                if (declaration.important()) {
                    throw error(
                            at,
                            "\"" + declaration.property() + "\" in @keyframes " + name.text()
                                    + " is !important, which a keyframe cannot be");
                }
            }
            for (var offset : offsets) {
                frames.add(new Keyframes.Frame(offset, declarations));
            }
            skipWhitespace();
        }
        advance(); // }
        return new Keyframes(name.text(), frames);
    }

    /// `from`, `to`, `40%`, comma-separated, up to the `{`.
    private List<Double> keyframeSelectors() {
        var offsets = new ArrayList<Double>();
        while (true) {
            skipWhitespace();
            var token = peek();
            if (token.isIdent("from")) {
                offsets.add(0.0);
            } else if (token.isIdent("to")) {
                offsets.add(1.0);
            } else if (token.is(TokenType.PERCENTAGE) && token.numeric() >= 0 && token.numeric() <= 100) {
                offsets.add(token.numeric() / 100);
            } else {
                throw error(token, "a keyframe is from, to or a percentage from 0% to 100%, not " + token.describe());
            }
            advance();
            skipWhitespace();
            if (peek().is(TokenType.COMMA)) {
                advance();
                continue;
            }
            if (peek().is(TokenType.OPEN_BRACE)) {
                return offsets;
            }
            throw error(peek(), "expected \",\" or \"{\" after a keyframe, found " + peek().describe());
        }
    }

    private StyleRule styleRule(MediaCondition media, boolean starting) {
        var selectors = selectorList();
        var declarations = declarationBlock();
        return new StyleRule(selectors, declarations, ruleOrder++, starting, media);
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
            refuseOutsideTheSubset(peek());
            throw error(peek(), "expected \",\" or \"{\" after a selector, found " + peek().describe());
        }
    }

    /// Throws the unsupported-feature error for a token that starts something
    /// selectors have elsewhere and this subset has not: `[attr]`, `+`, `~`.
    private void refuseOutsideTheSubset(Token token) {
        if (token.is(TokenType.OPEN_BRACKET)) {
            throw unsupported(token, "attribute selectors ([attr]) are not in this subset");
        }
        if (token.isDelim('+') || token.isDelim('~')) {
            throw unsupported(token, "sibling combinators (+ and ~) are not in this subset");
        }
        if (token.isDelim('|')) {
            throw unsupported(token, "namespaces are not in this subset");
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
            refuseOutsideTheSubset(peek());
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
            var combinator = j < n - 1 ? combinators.get(n - 2 - j) : Selector.Combinator.NONE;
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
        var structural = new ArrayList<Structural>();
        var start = peek();
        var sawAnything = false;
        refuseOutsideTheSubset(start);

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
                    throw unsupported(token, "pseudo-elements (::before, ::after) are not in this subset");
                }
                if (peek().is(TokenType.FUNCTION)) {
                    structural.add(functionalPseudoClass(advance()));
                    sawAnything = true;
                    continue;
                }
                if (!peek().is(TokenType.IDENT)) {
                    throw error(peek(), "expected a pseudo-class name after \":\", found " + peek().describe());
                }
                var name = advance();
                var position = Structural.parse(name.text());
                if (position != null) {
                    structural.add(position);
                    sawAnything = true;
                    continue;
                }
                var pseudo = Selector.PseudoClass.parse(name.text());
                if (pseudo == null) {
                    // Named rather than ignored: ":hovered" as a silently
                    // never-matching rule is a bad afternoon.
                    throw unsupported(
                            name,
                            "unknown pseudo-class \":" + name.text() + "\"; supported: " + supportedPseudoClasses());
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
        return new Selector.Compound(type, id, classes, pseudoClasses, structural);
    }

    /// `:nth-child(…)` or `:nth-last-child(…)`, from the function token to its
    /// `)`. Any other function, `:not()` or `:has()`, is outside the subset.
    private Structural functionalPseudoClass(Token function) {
        var name = function.text().toLowerCase(Locale.ROOT);
        if (!name.equals("nth-child") && !name.equals("nth-last-child")) {
            throw unsupported(
                    function,
                    "\":" + function.text() + "()\" is not in this subset; the functional pseudo-classes"
                            + " are :nth-child() and :nth-last-child()");
        }
        var formula = new StringBuilder();
        while (!peek().is(TokenType.CLOSE_PAREN)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.OPEN_BRACE)) {
                throw error(peek(), "\":" + name + "(\" is not closed");
            }
            formula.append(advance().cssText());
        }
        advance(); // )
        var position = nth(name.equals("nth-last-child"), formula.toString());
        if (position == null) {
            throw error(
                    function,
                    "\":" + name + "(" + formula.toString().trim() + ")\" is not odd, even, a number or An+B");
        }
        return position;
    }

    /// `An+B`, `An`, `n+B`, `-n+B` or `B`, with the spacing taken out. Group 1 is
    /// the coefficient of `n` when there is an `n`, group 2 the offset after it,
    /// and group 3 a lone number.
    private static final Pattern FORMULA = Pattern.compile("^(?:([+-]?\\d*)n([+-]\\d+)?|([+-]?\\d+))$");

    /// The position a formula names, or null when it is not one.
    private static Structural.@Nullable Nth nth(boolean fromEnd, String formula) {
        var text = formula.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        switch (text) {
            case "odd" -> {
                return new Structural.Nth(2, 1, fromEnd);
            }
            case "even" -> {
                return new Structural.Nth(2, 0, fromEnd);
            }
            default -> {}
        }
        var matcher = FORMULA.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        try {
            var lone = matcher.group(3);
            if (lone != null) {
                return new Structural.Nth(0, Integer.parseInt(lone), fromEnd);
            }
            var coefficient = Objects.requireNonNullElse(matcher.group(1), "");
            var step =
                    switch (coefficient) {
                        case "", "+" -> 1;
                        case "-" -> -1;
                        default -> Integer.parseInt(coefficient);
                    };
            var offset = matcher.group(2);
            return new Structural.Nth(step, offset == null ? 0 : Integer.parseInt(offset), fromEnd);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String supportedPseudoClasses() {
        var names = new ArrayList<String>();
        for (var value : Selector.PseudoClass.values()) {
            names.add(":" + value.cssName());
        }
        names.add(":first-child");
        names.add(":last-child");
        names.add(":only-child");
        names.add(":nth-child()");
        names.add(":nth-last-child()");
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
        var property = name.text().startsWith("--") ? name.text() : name.text().toLowerCase(Locale.ROOT);

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

    /// An error for something outside the subset rather than a mistake, which a
    /// lenient parse drops the rule over instead of refusing the sheet.
    private static CssSyntaxException unsupported(Token at, String message) {
        return new CssSyntaxException(message, at.line(), at.column(), true);
    }
}
