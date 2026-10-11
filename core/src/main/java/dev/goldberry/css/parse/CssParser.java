package dev.goldberry.css.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Declaration;
import dev.goldberry.css.FontFaceRule;
import dev.goldberry.css.Keyframes;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.container.ContainerQueries;
import dev.goldberry.css.container.ContainerQuery;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.media.MediaQueries;
import dev.goldberry.css.select.Functional;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.select.Structural;
import dev.goldberry.log.Logs;
import dev.goldberry.motion.Easing;

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
/// [DroppedRule]. A malformed sheet is refused in both. A rule nested inside
/// another is outside the subset, not malformed, so a lenient parse drops the
/// rule that holds it.
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
    /// @param imports   the `@import` rules at the top of the sheet, in order,
    ///                  for whoever loaded the sheet to resolve
    /// @param fontFaces the `@font-face` rules, in order
    /// @param layers    every `@layer` name the sheet mentions, in the order it
    ///                  first mentions it, nested ones as `outer.inner`
    public record Parsed(
            List<StyleRule> rules,
            List<Keyframes> keyframes,
            List<DroppedRule> dropped,
            List<Import> imports,
            List<FontFaceRule> fontFaces,
            List<String> layers) {

        public Parsed {
            rules = List.copyOf(rules);
            keyframes = List.copyOf(keyframes);
            dropped = List.copyOf(dropped);
            imports = List.copyOf(imports);
            fontFaces = List.copyOf(fontFaces);
            layers = List.copyOf(layers);
        }

        /// A parse that imports nothing, ships no face and names no layer.
        public Parsed(List<StyleRule> rules, List<Keyframes> keyframes, List<DroppedRule> dropped) {
            this(rules, keyframes, dropped, List.of(), List.of(), List.of());
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
    private final List<Import> imports = new ArrayList<>();
    private final List<FontFaceRule> fontFaces = new ArrayList<>();

    /// Every layer name met, in the order first met.
    private final List<String> layers = new ArrayList<>();

    /// The `@layer` block being read, as its full name; null outside one.
    private @Nullable String currentLayer;

    /// The `@container` blocks the rules being read are inside, outermost
    /// first: every one of them has to hold for a rule to apply.
    private List<ContainerQuery> currentContainers = List.of();

    /// How many unnamed `@layer` blocks have been met, which names the next.
    private int anonymousLayers;

    /// Whether an `@import` may still come: only before every other rule.
    private boolean importsAllowed = true;

    /// The selectors `&` stands for while a nested rule's selectors are read;
    /// null at the top level, where `&` is refused.
    private @Nullable List<Selector> nestingParent;

    /// Whether the selector being read wrote `&`, which makes it not relative.
    private boolean nestingSeen;

    /// Whether a `:has()`'s arguments are being read, inside which another
    /// `:has()` is refused.
    private boolean insideHas;

    /// How many functional pseudo-classes' arguments are being read, inside
    /// which a pseudo-element is refused.
    private int argumentDepth;

    /// The pseudo-element the compound just read ended with, `::placeholder`,
    /// for [#selector] to put on a part of its own.
    private @Nullable PseudoElement pendingPseudoElement;

    /// The `:is()` each `&` was read as, by identity, so that splicing tells
    /// them from an `:is()` an author wrote.
    private final Set<Functional> nestingMarkers = Collections.newSetFromMap(new IdentityHashMap<>());

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
        return new Parsed(rules, parser.keyframes, parser.dropped, parser.imports, parser.fontFaces, parser.layers);
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
            var emitted = into.size();
            if (!next.isAtKeyword("import")) {
                importsAllowed = false;
            }
            try {
                if (next.is(TokenType.AT_KEYWORD)) {
                    if (starting) {
                        throw error(next, "@starting-style holds style rules and nothing else");
                    }
                    atRule(into, media);
                } else {
                    styleRule(into, media, starting);
                }
            } catch (CssSyntaxException e) {
                if (mode == ParseMode.STRICT || !e.isUnsupportedFeature()) {
                    throw e;
                }
                // A rule flattened partway, its nested rules included, goes
                // whole.
                into.subList(emitted, into.size()).clear();
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
            case "keyframes" -> keyframes.add(keyframes(at).under(media));
            case "media" -> media(at, into, media);
            case "container" -> container(at, into, media);
            case "import" -> imports.add(importRule(at, media));
            case "layer" -> layer(at, into, media);
            case "font-face" -> {
                if (media != MediaCondition.ALWAYS) {
                    throw unsupported(
                            at, "@font-face inside @media is not in this subset; declare it at the top level");
                }
                fontFaces.add(fontFace(at));
            }
            case "supports" -> {
                if (supported(at)) {
                    advance(); // {
                    rules(into, media, false, true);
                    advance(); // }
                } else {
                    skipBlock();
                }
            }
            default ->
                throw unsupported(
                        at,
                        "unsupported at-rule \"@" + at.text()
                                + "\"; this subset has @media, @container, @supports, @starting-style and @keyframes"
                                + " and nothing else");
        }
    }

    /// `@container [<name>] <condition> { rules }`.
    ///
    /// Every rule inside carries the query, with any enclosing `@container`'s,
    /// and the cascade asks each against the element's containers. A query a
    /// container cannot answer never applies: refused under [ParseMode#STRICT],
    /// kept, never matched and warned about under [ParseMode#LENIENT].
    private void container(Token at, List<StyleRule> into, MediaCondition media) {
        var prelude = new ArrayList<Token>();
        while (!peek().is(TokenType.OPEN_BRACE)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.SEMICOLON)) {
                throw error(peek(), "@container has no block");
            }
            prelude.add(advance());
        }
        var query = ContainerQueries.parse(prelude);
        var reason = MediaCondition.unsupported(query.condition());
        if (reason.isPresent()) {
            if (mode == ParseMode.STRICT) {
                throw unsupported(at, "@container " + query + " cannot be evaluated: " + reason.get());
            }
            drop("@container " + query, "never applies: " + reason.get(), at);
        }
        var outer = currentContainers;
        var joined = new ArrayList<>(outer);
        joined.add(query);
        currentContainers = List.copyOf(joined);
        try {
            advance(); // {
            rules(into, media, false, true);
            advance(); // }
        } finally {
            currentContainers = outer;
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
        var combined = mediaCondition(at, enclosing);
        advance(); // {
        rules(into, combined, false, true);
        advance(); // }
    }

    /// An `@media` prelude, up to its `{`, joined with `enclosing`: refused
    /// when a feature cannot be answered and the sheet is strict, kept and
    /// reported when it is lenient.
    private MediaCondition mediaCondition(Token at, MediaCondition enclosing) {
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
        return enclosing != MediaCondition.ALWAYS ? new MediaCondition.And(List.of(enclosing, condition)) : condition;
    }

    /// `@import <url> [supports(…)] [<media list>];` at the top of the sheet.
    ///
    /// Read, not loaded. An `@import` after any other rule is dropped, as CSS
    /// ignores it, and so is one inside `@media` or `@supports`, which CSS does
    /// not allow. `layer(…)` is outside the subset.
    private Import importRule(Token at, MediaCondition enclosing) {
        if (!importsAllowed || enclosing != MediaCondition.ALWAYS) {
            // CSS ignores one, so a lenient sheet drops it and reads on.
            throw unsupported(at, "an @import comes before every other rule of the sheet, and outside any block");
        }
        skipWhitespace();
        var href = importHref(at);
        var prelude = new ArrayList<Token>();
        while (!peek().is(TokenType.SEMICOLON)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.OPEN_BRACE)) {
                throw error(peek(), "an @import ends with \";\"");
            }
            prelude.add(advance());
        }
        advance(); // ;
        var rest = trimWhitespace(prelude);
        String layer = null;
        if (!rest.isEmpty() && rest.getFirst().isIdent("layer")) {
            layer = declareLayer(null);
            rest = trimWhitespace(rest.subList(1, rest.size()));
        } else if (!rest.isEmpty()
                && rest.getFirst().is(TokenType.FUNCTION)
                && rest.getFirst().text().equalsIgnoreCase("layer")) {
            var close = 1;
            var name = new StringBuilder();
            while (close < rest.size() && !rest.get(close).is(TokenType.CLOSE_PAREN)) {
                name.append(rest.get(close).cssText());
                close++;
            }
            if (close >= rest.size()) {
                throw error(rest.getFirst(), "an unclosed layer(");
            }
            layer = declareLayer(layerName(name.toString().trim(), rest.getFirst()));
            rest = trimWhitespace(rest.subList(close + 1, rest.size()));
        }
        if (!rest.isEmpty()
                && rest.getFirst().is(TokenType.FUNCTION)
                && rest.getFirst().text().equalsIgnoreCase("supports")) {
            var close = 1;
            var depth = 1;
            while (close < rest.size() && depth > 0) {
                var token = rest.get(close);
                if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                    depth++;
                } else if (token.is(TokenType.CLOSE_PAREN)) {
                    depth--;
                }
                close++;
            }
            if (depth != 0) {
                throw error(rest.getFirst(), "an unclosed supports(");
            }
            var condition = new ArrayList<Token>();
            condition.add(Token.of(TokenType.OPEN_PAREN, "(", at.line(), at.column()));
            condition.addAll(rest.subList(1, close));
            if (!Supports.holds(condition)) {
                // Imported never: a condition that does not hold is a sheet that
                // is not there, as in CSS.
                return new Import(href, MediaCondition.NEVER, layer, at.line(), at.column());
            }
            rest = trimWhitespace(rest.subList(close, rest.size()));
        }
        var media = rest.isEmpty() ? MediaCondition.ALWAYS : MediaQueries.parse(rest);
        return new Import(href, media, layer, at.line(), at.column());
    }

    /// `@layer a, b;`, which says in which order layers rank, or
    /// `@layer [name] { rules }`, whose rules go in the layer.
    ///
    /// A layer inside another is `outer.inner`. An unnamed block is a layer of
    /// its own that nothing else can name.
    private void layer(Token at, List<StyleRule> into, MediaCondition media) {
        var names = new ArrayList<String>();
        var name = new StringBuilder();
        while (!peek().is(TokenType.OPEN_BRACE) && !peek().is(TokenType.SEMICOLON)) {
            if (peek().is(TokenType.EOF)) {
                throw error(at, "@layer has no block and no \";\"");
            }
            var token = advance();
            if (token.is(TokenType.COMMA)) {
                names.add(layerName(name.toString().trim(), at));
                name.setLength(0);
            } else if (!token.is(TokenType.WHITESPACE)) {
                name.append(token.cssText());
            }
        }
        if (!name.isEmpty()) {
            names.add(layerName(name.toString().trim(), at));
        }
        if (peek().is(TokenType.SEMICOLON)) {
            advance();
            if (names.isEmpty()) {
                throw error(at, "an @layer statement names at least one layer");
            }
            for (var each : names) {
                declareLayer(each);
            }
            return;
        }
        if (names.size() > 1) {
            throw error(at, "an @layer block is one layer, not " + names.size());
        }
        var outer = currentLayer;
        currentLayer = declareLayer(names.isEmpty() ? null : names.getFirst());
        try {
            advance(); // {
            rules(into, media, false, true);
            advance(); // }
        } finally {
            currentLayer = outer;
        }
    }

    /// `name` as a layer name, refusing what is not one: identifiers joined by
    /// dots.
    private static String layerName(String name, Token at) {
        if (!name.matches("-?[_a-zA-Z][-_a-zA-Z0-9]*(\\.-?[_a-zA-Z][-_a-zA-Z0-9]*)*")) {
            throw error(at, "\"" + name + "\" is not a layer name");
        }
        return name;
    }

    /// Records the layer `name` inside the current one, or a new unnamed one
    /// for null, and answers its full name.
    private String declareLayer(@Nullable String name) {
        var local = name == null ? "\u0000" + anonymousLayers++ : name;
        var full = currentLayer == null ? local : currentLayer + "." + local;
        if (!layers.contains(full)) {
            layers.add(full);
        }
        return full;
    }

    /// `@font-face { font-family: …; src: …; font-weight: …; font-style: … }`.
    ///
    /// `src` is a list, and the first `url()` this toolkit can read is taken:
    /// one with no `format()`, or `truetype`, `opentype`, or their
    /// `-variations` forms. `local()` names a face the desktop has, which is
    /// not looked for, and WOFF is a compressed wrapper the rasterizer does not
    /// read. `font-weight` is a number, `normal` or `bold`, and a range takes
    /// its first number; `font-style` is `normal` or `italic`. `unicode-range`
    /// and `font-display` are read and set aside.
    private FontFaceRule fontFace(Token at) {
        skipWhitespace();
        var declarations = declarationBlock();
        String family = null;
        String src = null;
        var weight = 400;
        var style = BundledFont.Style.UPRIGHT;
        for (var declaration : declarations) {
            var value = declaration.value().stream()
                    .filter(t -> !t.is(TokenType.WHITESPACE))
                    .toList();
            switch (declaration.property()) {
                case "font-family" -> {
                    var name = new StringBuilder();
                    for (var token : declaration.value()) {
                        if (token.is(TokenType.COMMA)) {
                            break;
                        }
                        name.append(token.is(TokenType.STRING) ? token.text() : token.cssText());
                    }
                    family = name.toString().trim();
                }
                case "src" -> src = fontSrc(declaration.value());
                case "font-weight" -> {
                    if (value.isEmpty()) {
                        throw error(at, "@font-face font-weight has no value");
                    }
                    var first = value.getFirst();
                    if (first.isIdent("normal")) {
                        weight = 400;
                    } else if (first.isIdent("bold")) {
                        weight = 700;
                    } else if (first.is(TokenType.NUMBER) && first.numeric() >= 1 && first.numeric() <= 1000) {
                        weight = (int) Math.round(first.numeric());
                    } else {
                        throw error(first, "an @font-face weight is a number from 1 to 1000, normal or bold");
                    }
                }
                case "font-style" -> {
                    if (value.size() == 1 && value.getFirst().isIdent("italic")) {
                        style = BundledFont.Style.ITALIC;
                    } else if (value.size() == 1 && value.getFirst().isIdent("normal")) {
                        style = BundledFont.Style.UPRIGHT;
                    } else {
                        throw unsupported(at, "an @font-face style is normal or italic; an oblique face is not drawn");
                    }
                }
                case "unicode-range",
                        "font-display",
                        "font-stretch",
                        "font-feature-settings",
                        "ascent-override",
                        "descent-override",
                        "line-gap-override",
                        "size-adjust" -> {
                    // Set aside: the face is used for whatever it has.
                }
                default ->
                    throw unsupported(at, "\"" + declaration.property() + "\" is not a descriptor @font-face has here");
            }
        }
        if (family == null || family.isEmpty()) {
            throw error(at, "an @font-face names its font-family");
        }
        if (src == null) {
            throw unsupported(
                    at,
                    "an @font-face for \"" + family + "\" has no src this toolkit reads: it reads"
                            + " TrueType and OpenType, through url(), and not local() or WOFF");
        }
        return new FontFaceRule(family, weight, style, src, at.line(), at.column());
    }

    /// The first source in an `src` list that this toolkit reads, or null.
    private static @Nullable String fontSrc(List<Token> value) {
        for (var entry : commaSeparated(value)) {
            var tokens = entry.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
            if (tokens.isEmpty()
                    || !tokens.getFirst().is(TokenType.FUNCTION)
                    || !tokens.getFirst().text().equalsIgnoreCase("url")) {
                continue;
            }
            var href = new StringBuilder();
            var i = 1;
            for (; i < tokens.size() && !tokens.get(i).is(TokenType.CLOSE_PAREN); i++) {
                var part = tokens.get(i);
                href.append(part.is(TokenType.STRING) ? part.text() : part.cssText());
            }
            var format = "";
            for (var j = i + 1; j < tokens.size(); j++) {
                if (tokens.get(j).is(TokenType.FUNCTION)
                        && tokens.get(j).text().equalsIgnoreCase("format")
                        && j + 1 < tokens.size()) {
                    format = tokens.get(j + 1).text().toLowerCase(Locale.ROOT);
                }
            }
            var readable = switch (format) {
                case "", "truetype", "opentype", "truetype-variations", "opentype-variations" -> true;
                default -> false;
            };
            var lower = href.toString().toLowerCase(Locale.ROOT);
            if (readable && !lower.endsWith(".woff") && !lower.endsWith(".woff2") && !href.isEmpty()) {
                return href.toString();
            }
        }
        return null;
    }

    /// `value` split on the commas outside any function.
    private static List<List<Token>> commaSeparated(List<Token> value) {
        var entries = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.COMMA)) {
                entries.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        entries.add(List.copyOf(current));
        return entries;
    }

    /// The address of an `@import`: a string, or `url(…)` quoted or not.
    private String importHref(Token at) {
        var token = peek();
        if (token.is(TokenType.STRING)) {
            advance();
            return token.text();
        }
        if (token.is(TokenType.FUNCTION) && token.text().equalsIgnoreCase("url")) {
            advance();
            var href = new StringBuilder();
            while (!peek().is(TokenType.CLOSE_PAREN)) {
                if (peek().is(TokenType.EOF) || peek().is(TokenType.SEMICOLON)) {
                    throw error(peek(), "an unclosed url(");
                }
                var part = advance();
                href.append(part.is(TokenType.STRING) ? part.text() : part.cssText());
            }
            advance(); // )
            return href.toString().trim();
        }
        throw error(at, "an @import names its sheet with a string or url(), not " + token.describe());
    }

    /// Whether the `@supports` prelude after `at` holds, read up to its `{`,
    /// which is left for the caller.
    private boolean supported(Token at) {
        var prelude = new ArrayList<Token>();
        while (!peek().is(TokenType.OPEN_BRACE)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.SEMICOLON)) {
                throw error(peek(), "@supports has no block");
            }
            prelude.add(advance());
        }
        try {
            return Supports.holds(prelude);
        } catch (IllegalArgumentException e) {
            throw error(at, "@supports " + squeeze(text(prelude)) + " is not a condition: " + e.getMessage());
        }
    }

    /// The block at the parser, `{` to its `}`, read past and not parsed: an
    /// `@supports` block whose condition does not hold.
    private void skipBlock() {
        expect(TokenType.OPEN_BRACE, "{");
        var depth = 1;
        while (depth > 0) {
            var token = advance();
            if (token.is(TokenType.EOF)) {
                throw error(token, "unclosed block");
            }
            if (token.is(TokenType.OPEN_BRACE)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_BRACE)) {
                depth--;
            }
        }
    }

    private static String text(List<Token> tokens) {
        var text = new StringBuilder();
        for (var token : tokens) {
            text.append(token.cssText());
        }
        return text.toString();
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

    /// A style rule, and every rule nested in it, flattened into `into`.
    private void styleRule(List<StyleRule> into, MediaCondition media, boolean starting) {
        var selectors = selectorList();
        block(into, selectors, media, starting);
    }

    /// A style rule's block: its declarations, and the rules nested among
    /// them, flattened into ordinary rules in source order.
    ///
    /// The declarations before the first nested rule are the rule itself, and
    /// each run of declarations after a nested rule is a rule of its own with
    /// the same selectors, placed after it, which is what CSS's nesting says
    /// about order. A block with no nested rule is one rule even when empty.
    ///
    /// A nested rule that asks for something outside the subset is dropped by
    /// itself in a lenient sheet; the rest of the block stays.
    private void block(List<StyleRule> into, List<Selector> selectors, MediaCondition media, boolean starting) {
        expect(TokenType.OPEN_BRACE, "{");
        var run = new ArrayList<Declaration>();
        var sawNested = false;
        while (true) {
            skipWhitespace();
            if (peek().is(TokenType.CLOSE_BRACE)) {
                advance();
                if (!run.isEmpty() || !sawNested) {
                    into.add(new StyleRule(
                            selectors, run, ruleOrder++, starting, media, currentLayer, currentContainers));
                }
                return;
            }
            if (peek().is(TokenType.EOF)) {
                throw error(peek(), "unclosed declaration block");
            }
            if (peek().is(TokenType.SEMICOLON)) {
                // An empty declaration. Legal, and means nothing.
                advance();
                continue;
            }
            if (startsNestedRule()) {
                if (!run.isEmpty()) {
                    into.add(new StyleRule(
                            selectors,
                            List.copyOf(run),
                            ruleOrder++,
                            starting,
                            media,
                            currentLayer,
                            currentContainers));
                    run.clear();
                }
                sawNested = true;
                nestedRule(into, selectors, media, starting);
                continue;
            }
            var declaration = declaration();
            if (mode == ParseMode.STRICT) {
                refuseForeignCurves(declaration);
            }
            run.add(declaration);
        }
    }

    /// One rule nested in a block: `@media … { … }`, or a selector list and a
    /// block of its own, resolved against `parent`.
    private void nestedRule(List<StyleRule> into, List<Selector> parent, MediaCondition media, boolean starting) {
        var start = index;
        var emitted = into.size();
        try {
            if (peek().is(TokenType.AT_KEYWORD)) {
                var at = advance();
                if (at.text().equalsIgnoreCase("supports")) {
                    if (supported(at)) {
                        block(into, parent, media, starting);
                    } else {
                        skipBlock();
                    }
                    return;
                }
                if (!at.text().equalsIgnoreCase("media")) {
                    throw unsupported(
                            at,
                            "\"@" + at.text() + "\" inside a rule is not in this subset; @media and @supports are"
                                    + " the at-rules a rule may hold");
                }
                block(into, parent, mediaCondition(at, media), starting);
                return;
            }
            block(into, nestedSelectorList(parent), media, starting);
        } catch (CssSyntaxException e) {
            if (mode == ParseMode.STRICT || !e.isUnsupportedFeature()) {
                throw e;
            }
            into.subList(emitted, into.size()).clear();
            index = start;
            var at = peek();
            var text = skipRule();
            drop(text, reason(e), at);
        }
    }

    /// A nested rule's selectors, each joined to `parent`.
    ///
    /// `&` is the parent: `&:hover`, `.dark &`, `& > b`. A selector with no `&`
    /// is relative to it, `b` being `& b` and `> b` being `& > b`. The parent is
    /// `:is(…)` of its selectors, which is CSS's own reading and gives CSS's
    /// specificity; a parent of one selector is written into the nested one
    /// instead, which matches and weighs the same and keeps the resolver's type
    /// buckets exact.
    private List<Selector> nestedSelectorList(List<Selector> parent) {
        var outer = nestingParent;
        nestingParent = parent;
        try {
            var selectors = new ArrayList<Selector>();
            while (true) {
                skipWhitespace();
                selectors.add(nestedSelector(parent));
                skipWhitespace();
                if (peek().is(TokenType.COMMA)) {
                    advance();
                    continue;
                }
                if (peek().is(TokenType.OPEN_BRACE)) {
                    return selectors;
                }
                refuseOutsideTheSubset(peek());
                throw error(peek(), "expected \",\" or \"{\" after a selector, found " + peek().describe());
            }
        } finally {
            nestingParent = outer;
        }
    }

    /// One nested selector, joined to `parent`.
    private Selector nestedSelector(List<Selector> parent) {
        var leading = explicitCombinator(peek());
        if (leading != null) {
            advance();
            skipWhitespace();
        } else {
            refuseOutsideTheSubset(peek());
        }
        nestingSeen = false;
        var selector = selector();
        if (leading != null || !nestingSeen) {
            // Relative: the parent stands to the left of the whole selector.
            var parts = new ArrayList<>(selector.parts());
            var leftmost = parts.removeLast();
            parts.add(
                    new Selector.Part(leftmost.compound(), leading != null ? leading : Selector.Combinator.DESCENDANT));
            parts.add(new Selector.Part(nesting(parent), Selector.Combinator.NONE));
            selector = new Selector(parts);
        }
        return parent.size() == 1 ? spliced(selector, parent.getFirst()) : selector;
    }

    /// The compound `&` stands for alone: `:is(parent)`.
    private Selector.Compound nesting(List<Selector> parent) {
        return new Selector.Compound(null, null, null, null, null, List.of(nestingMarker(parent)));
    }

    /// What `&` is read as, `:is(parent)`, marked so [#spliced] can find it
    /// again.
    private Functional nestingMarker(List<Selector> parent) {
        var is = new Functional.Is(parent);
        nestingMarkers.add(is);
        return is;
    }

    /// `selector` with each `&` that stands for `parent` written out: the
    /// parent's parts in its place, and whatever the nested compound adds to
    /// `&` (`&:hover`, `&.open`) added to the parent's own rightmost compound.
    /// A compound that cannot be merged, one naming a second type or id, keeps
    /// its `:is()`.
    private Selector spliced(Selector selector, Selector parent) {
        var parts = new ArrayList<Selector.Part>();
        for (var part : selector.parts()) {
            var compound = part.compound();
            var marker = compound.functional().stream()
                    .filter(f -> nestingMarkers.contains(f))
                    .findFirst()
                    .orElse(null);
            var merged = marker == null ? null : merged(compound, marker, parent.key());
            if (merged == null) {
                parts.add(part);
                continue;
            }
            var parentParts = parent.parts();
            parts.add(new Selector.Part(
                    merged,
                    parentParts.size() == 1
                            ? part.combinator()
                            : parentParts.getFirst().combinator()));
            for (var i = 1; i < parentParts.size(); i++) {
                var combinator = i == parentParts.size() - 1
                        ? part.combinator()
                        : parentParts.get(i).combinator();
                parts.add(new Selector.Part(parentParts.get(i).compound(), combinator));
            }
        }
        return new Selector(parts);
    }

    /// `compound` with the `&` marker taken out and `key`'s simple selectors
    /// put in, or null when the two name different types or ids.
    private static Selector.@Nullable Compound merged(
            Selector.Compound compound, Functional marker, Selector.Compound key) {
        if (compound.type() != null && key.type() != null && !compound.type().equals(key.type())) {
            return null;
        }
        if (compound.id() != null && key.id() != null && !compound.id().equals(key.id())) {
            return null;
        }
        var classes = new ArrayList<>(key.classes());
        classes.addAll(compound.classes());
        var pseudoClasses = new ArrayList<>(key.pseudoClasses());
        pseudoClasses.addAll(compound.pseudoClasses());
        var structural = new ArrayList<>(key.structural());
        structural.addAll(compound.structural());
        var functional = new ArrayList<>(key.functional());
        for (var f : compound.functional()) {
            if (f != marker) {
                functional.add(f);
            }
        }
        return new Selector.Compound(
                key.type() != null ? key.type() : compound.type(),
                key.id() != null ? key.id() : compound.id(),
                classes,
                pseudoClasses,
                structural,
                functional,
                key.pseudoElement() != null ? key.pseudoElement() : compound.pseudoElement());
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
    /// selectors have elsewhere and this subset has not: `[attr]`, `|`.
    private void refuseOutsideTheSubset(Token token) {
        if (token.is(TokenType.OPEN_BRACKET)) {
            throw unsupported(token, "attribute selectors ([attr]) are not in this subset");
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
        partFor(compounds, combinators);

        while (true) {
            if (compounds.getLast().pseudoElement() != null) {
                endOfPseudoElement();
                break;
            }
            var sawWhitespace = skipWhitespace();
            var explicit = explicitCombinator(peek());
            if (explicit != null) {
                advance();
                skipWhitespace();
                combinators.add(explicit);
                compounds.add(compound());
                partFor(compounds, combinators);
                continue;
            }
            // Whitespace is only a combinator when something selectable follows;
            // the space in ".a { " is just spacing.
            if (sawWhitespace && startsCompound()) {
                combinators.add(Selector.Combinator.DESCENDANT);
                compounds.add(compound());
                partFor(compounds, combinators);
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

    /// When the compound just read ended with a pseudo-element,
    /// `text-input::placeholder`, adds the part that stands for it, joined to
    /// that compound by [Selector.Combinator#PART].
    private void partFor(List<Selector.Compound> compounds, List<Selector.Combinator> combinators) {
        var pseudo = pendingPseudoElement;
        if (pseudo == null) {
            return;
        }
        pendingPseudoElement = null;
        combinators.add(Selector.Combinator.PART);
        compounds.add(Selector.Compound.of(pseudo));
    }

    /// A pseudo-element ends its selector: what may follow is whitespace and
    /// the end of the selector, and nothing else is in the subset.
    private void endOfPseudoElement() {
        var at = index;
        while (tokens.get(at).is(TokenType.WHITESPACE)) {
            at++;
        }
        var next = tokens.get(at);
        if (next.is(TokenType.COMMA)
                || next.is(TokenType.OPEN_BRACE)
                || next.is(TokenType.CLOSE_PAREN)
                || next.is(TokenType.EOF)) {
            return;
        }
        throw unsupported(next, "a pseudo-element ends its selector; nothing may follow it in this subset");
    }

    /// The combinator a token spells, `>`, `+` or `~`, or null for any other.
    private static Selector.@Nullable Combinator explicitCombinator(Token token) {
        if (token.isDelim('>')) {
            return Selector.Combinator.CHILD;
        }
        if (token.isDelim('+')) {
            return Selector.Combinator.NEXT_SIBLING;
        }
        if (token.isDelim('~')) {
            return Selector.Combinator.SUBSEQUENT_SIBLING;
        }
        return null;
    }

    /// A relative selector, `> a b`, `+ a` or `a`: the combinator before it
    /// (a descendant when none is written) is put on its leftmost part, which
    /// is how [Functional.Has] reads where it starts.
    private Selector relativeSelector() {
        skipWhitespace();
        var leading = explicitCombinator(peek());
        if (leading != null) {
            advance();
            skipWhitespace();
        } else {
            leading = Selector.Combinator.DESCENDANT;
        }
        var selector = selector();
        var parts = new ArrayList<>(selector.parts());
        parts.add(new Selector.Part(parts.removeLast().compound(), leading));
        return new Selector(parts);
    }

    private boolean startsCompound() {
        var token = peek();
        return token.is(TokenType.IDENT)
                || token.is(TokenType.HASH)
                || token.is(TokenType.COLON)
                || token.isDelim('.')
                || token.isDelim('*')
                || token.isDelim('&');
    }

    /// `button.primary:hover`, with no whitespace inside it.
    private Selector.Compound compound() {
        String type = null;
        String id = null;
        var classes = new ArrayList<String>();
        var pseudoClasses = new ArrayList<Selector.PseudoClass>();
        var structural = new ArrayList<Structural>();
        var functional = new ArrayList<Functional>();
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
            } else if (token.isDelim('&')) {
                if (nestingParent == null) {
                    throw unsupported(token, "\"&\" outside a nested rule is not in this subset");
                }
                advance();
                functional.add(nestingMarker(nestingParent));
                nestingSeen = true;
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
                    advance();
                    var pseudo = peek().is(TokenType.IDENT) ? PseudoElement.parse(peek().text()) : null;
                    if (pseudo == null) {
                        throw unsupported(
                                token,
                                "pseudo-elements other than ::placeholder and ::selection (::before, ::after) are"
                                        + " not in this subset");
                    }
                    if (argumentDepth > 0) {
                        throw unsupported(token, "a pseudo-element inside a functional pseudo-class is not in CSS");
                    }
                    advance();
                    if (!sawAnything) {
                        // `::selection` alone, or after a combinator: the
                        // part itself, below anything.
                        return Selector.Compound.of(pseudo);
                    }
                    pendingPseudoElement = pseudo;
                    break;
                }
                if (peek().is(TokenType.FUNCTION)) {
                    switch (functionalPseudoClass(advance())) {
                        case Structural position -> structural.add(position);
                        case Functional taking -> functional.add(taking);
                        default -> throw new IllegalStateException("a pseudo-class is structural or functional");
                    }
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
        return new Selector.Compound(type, id, classes, pseudoClasses, structural, functional);
    }

    /// `:nth-child(…)`, `:nth-last-child(…)`, `:nth-of-type(…)`,
    /// `:nth-last-of-type(…)`, `:is(…)`, `:where(…)`, `:not(…)` or `:has(…)`, from the
    /// function token to its `)`: a [Structural] for the four positions, a
    /// [Functional] for the three that take a selector list and for
    /// `:nth-child(An+B of S)` and `:has()`. Any other function is outside the
    /// subset.
    private Object functionalPseudoClass(Token function) {
        var name = function.text().toLowerCase(Locale.ROOT);
        switch (name) {
            case "is" -> {
                return new Functional.Is(argumentSelectors(function));
            }
            case "where" -> {
                return new Functional.Where(argumentSelectors(function));
            }
            case "not" -> {
                return new Functional.Not(argumentSelectors(function));
            }
            case "has" -> {
                return new Functional.Has(relativeSelectors(function));
            }
            default -> {}
        }
        var ofType = name.equals("nth-of-type") || name.equals("nth-last-of-type");
        if (!name.equals("nth-child") && !name.equals("nth-last-child") && !ofType) {
            throw unsupported(
                    function,
                    "\":" + function.text() + "()\" is not in this subset; the functional pseudo-classes"
                            + " are :is(), :where(), :not(), :has(), :nth-child(), :nth-last-child(),"
                            + " :nth-of-type() and :nth-last-of-type()");
        }
        var formula = new StringBuilder();
        List<Selector> of = null;
        while (!peek().is(TokenType.CLOSE_PAREN)) {
            if (peek().is(TokenType.EOF) || peek().is(TokenType.OPEN_BRACE)) {
                throw error(peek(), "\":" + name + "(\" is not closed");
            }
            // `of S` filters the siblings counted by a selector list. It ends
            // the formula, and the list runs to the `)`, which it consumes.
            if (peek().isIdent("of")) {
                if (ofType) {
                    throw error(peek(), "\":" + name + "()\" counts by type and takes no \"of S\"");
                }
                advance();
                of = argumentSelectors(function);
                break;
            }
            formula.append(advance().cssText());
        }
        if (of == null) {
            advance(); // )
        }
        var position = nth(name.startsWith("nth-last-"), formula.toString());
        if (position == null) {
            throw error(
                    function,
                    "\":" + name + "(" + formula.toString().trim() + ")\" is not odd, even, a number or An+B");
        }
        if (of != null) {
            return new Functional.NthOf(position, of);
        }
        return ofType ? new Structural.OfType(position) : position;
    }

    /// The selector list inside a functional pseudo-class, up to and including
    /// its `)`. Each argument is a complex selector; one outside the subset
    /// drops the whole rule, as it would anywhere else in it.
    private List<Selector> argumentSelectors(Token function) {
        argumentDepth++;
        try {
            return argumentList(function);
        } finally {
            argumentDepth--;
        }
    }

    private List<Selector> argumentList(Token function) {
        var selectors = new ArrayList<Selector>();
        while (true) {
            skipWhitespace();
            if (peek().is(TokenType.CLOSE_PAREN)) {
                throw error(peek(), "\":" + function.text() + "()\" needs a selector");
            }
            selectors.add(selector());
            skipWhitespace();
            if (peek().is(TokenType.COMMA)) {
                advance();
                continue;
            }
            if (peek().is(TokenType.CLOSE_PAREN)) {
                advance();
                return selectors;
            }
            refuseOutsideTheSubset(peek());
            throw error(
                    peek(), "expected \",\" or \")\" in \":" + function.text() + "()\", found " + peek().describe());
        }
    }

    /// The relative selectors inside `:has()`, up to and including its `)`.
    ///
    /// A `:has()` inside another is refused, as CSS refuses it: the search it
    /// asks for would nest one subtree walk in another.
    private List<Selector> relativeSelectors(Token function) {
        if (insideHas) {
            throw unsupported(function, "\":has()\" inside \":has()\" is not in CSS");
        }
        insideHas = true;
        argumentDepth++;
        try {
            var selectors = new ArrayList<Selector>();
            while (true) {
                skipWhitespace();
                if (peek().is(TokenType.CLOSE_PAREN)) {
                    throw error(peek(), "\":has()\" needs a selector");
                }
                selectors.add(relativeSelector());
                skipWhitespace();
                if (peek().is(TokenType.COMMA)) {
                    advance();
                    continue;
                }
                if (peek().is(TokenType.CLOSE_PAREN)) {
                    advance();
                    return selectors;
                }
                refuseOutsideTheSubset(peek());
                throw error(peek(), "expected \",\" or \")\" in \":has()\", found " + peek().describe());
            }
        } finally {
            insideHas = false;
            argumentDepth--;
        }
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
            var step = switch (coefficient) {
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
        names.add(":first-of-type");
        names.add(":last-of-type");
        names.add(":only-of-type");
        names.add(":nth-of-type()");
        names.add(":nth-last-of-type()");
        names.add(":is()");
        names.add(":where()");
        names.add(":not()");
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
            if (startsNestedRule()) {
                throw unsupported(peek(), "a keyframe holds declarations and no rules");
            }
            var declaration = declaration();
            if (mode == ParseMode.STRICT) {
                refuseForeignCurves(declaration);
            }
            declarations.add(declaration);
        }
    }

    /// The properties a timing function is written in.
    private static final Set<String> TIMED =
            Set.of("transition", "transition-timing-function", "animation", "animation-timing-function");

    /// Refuses, in a strict sheet, a timing function that is not one of the
    /// design system's three curves: `ease`, `steps(4)`, `cubic-bezier(…)`.
    ///
    /// A strict sheet is the toolkit's own, and the toolkit's controls move on
    /// the motion language's curves and no others. An application's sheet,
    /// read leniently, may write any curve CSS has.
    private static void refuseForeignCurves(Declaration declaration) {
        if (!TIMED.contains(declaration.property())) {
            return;
        }
        var depth = 0;
        for (var token : declaration.value()) {
            if (depth == 0 && token.is(TokenType.FUNCTION) && isCurveFunction(token.text())) {
                throw unsupported(token, foreignCurve(token.text() + "()"));
            }
            if (depth == 0 && token.is(TokenType.IDENT) && isForeignKeyword(token.text())) {
                throw unsupported(token, foreignCurve(token.text()));
            }
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
            }
        }
    }

    private static boolean isCurveFunction(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("cubic-bezier") || lower.equals("steps") || lower.equals("linear");
    }

    private static boolean isForeignKeyword(String word) {
        return Easing.parse(word) != null
                && Easing.BUILT_IN.stream().noneMatch(curve -> curve.cssName().equalsIgnoreCase(word));
    }

    private static String foreignCurve(String written) {
        return "the timing function \"" + written + "\" is not one of the design system's curves; the toolkit's"
                + " own sheets use ease-enter, ease-exit and linear";
    }

    /// Whether the block's next item is a rule rather than a declaration:
    /// `&:hover { … }`, `b { … }`, `> b { … }` or `@media … { … }`.
    ///
    /// CSS tells the two apart the same way: an at-rule is never a declaration,
    /// and a declaration whose value would hold a `{}` block is reparsed as a
    /// rule. So the test is whether a `{` comes before the `;` or `}` that would
    /// end a declaration, outside any parentheses. A custom property is the one
    /// declaration whose value may hold a block, and is left to [#declaration].
    ///
    /// Asked so that a nested rule is *outside the subset* rather than
    /// *malformed*: a lenient sheet then drops the rule holding it instead of
    /// being refused whole.
    private boolean startsNestedRule() {
        var first = peek();
        if (first.is(TokenType.AT_KEYWORD)) {
            return true;
        }
        if (first.is(TokenType.IDENT) && first.text().startsWith("--")) {
            return false;
        }
        var depth = 0;
        for (var i = index; i < tokens.size(); i++) {
            var token = tokens.get(i);
            switch (token.type()) {
                case FUNCTION, OPEN_PAREN, OPEN_BRACKET -> depth++;
                case CLOSE_PAREN, CLOSE_BRACKET -> depth--;
                case OPEN_BRACE -> {
                    if (depth <= 0) {
                        return true;
                    }
                }
                case SEMICOLON, CLOSE_BRACE -> {
                    if (depth <= 0) {
                        return false;
                    }
                }
                case EOF -> {
                    return false;
                }
                default -> {}
            }
        }
        return false;
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
