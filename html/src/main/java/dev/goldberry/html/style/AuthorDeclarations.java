package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Declaration;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.html.model.Element;

/// What a mail may say about its own appearance: the declarations of a `style=`
/// or a `<style>` rule, read and cut down to what is safe and supported.
///
/// ## What is kept
///
/// Colour, the text's size, weight, style, spacing, alignment, decoration and
/// case, a box's padding, margin, border, corners and `max-width`, `opacity`,
/// `visibility`, and `display: none` (which is how a mail hides its preview line).
/// A property the toolkit's cascade does not know is not kept either, so this
/// list can only shrink at run time.
///
/// ## What is refused, and why
///
/// - **Anything that fetches.** `url()`, `image-set()` and every function not on
///   the short list of colour and arithmetic ones are refused, so a mail cannot
///   load a tracking pixel through a `background`. `background` is kept only when
///   its value is a colour, and becomes `background-color`.
/// - **Anything that leaves the view.** `position`, `z-index`, `transform`,
///   `float` and a negative margin are not on the list, so nothing a mail says
///   can draw over the application round it.
/// - **Anything that reaches the theme.** A custom property and `var()` are
///   refused: `--gb-accent` is the application's, and a mail has no business
///   reading or setting it.
/// - **Anything that moves.** `animation` and `transition`.
/// - **`!important`**, which is dropped from the declaration rather than the
///   declaration refused: a mail's styles rank above the toolkit's own sheets
///   already, and an application's `!important` has to stay the way it
///   insists.
/// - **A family.** `font-family` names fonts the application has not
///   shipped, so a mail reads in the application's own face.
/// - A backslash, a brace or a semicolon inside a value, which is a declaration
///   trying to escape the one it is in.
final class AuthorDeclarations {

    private AuthorDeclarations() {}

    /// The properties a mail may set.
    private static final Set<String> ALLOWED = Set.of(
            "color",
            "background-color",
            "background",
            "opacity",
            "font-size",
            "font-weight",
            "font-style",
            "line-height",
            "letter-spacing",
            "word-spacing",
            "text-align",
            "text-decoration",
            "text-decoration-line",
            "text-decoration-color",
            "text-decoration-style",
            "text-decoration-thickness",
            "text-transform",
            "white-space",
            "hyphens",
            "padding",
            "padding-top",
            "padding-right",
            "padding-bottom",
            "padding-left",
            "margin",
            "margin-top",
            "margin-right",
            "margin-bottom",
            "margin-left",
            "border",
            "border-top",
            "border-right",
            "border-bottom",
            "border-left",
            "border-width",
            "border-style",
            "border-color",
            "border-top-width",
            "border-right-width",
            "border-bottom-width",
            "border-left-width",
            "border-top-style",
            "border-right-style",
            "border-bottom-style",
            "border-left-style",
            "border-top-color",
            "border-right-color",
            "border-bottom-color",
            "border-left-color",
            "border-radius",
            "border-top-left-radius",
            "border-top-right-radius",
            "border-bottom-right-radius",
            "border-bottom-left-radius",
            "max-width",
            "display",
            "visibility");

    /// The functions a value may call: colours and arithmetic, none of which can
    /// reach outside the value.
    private static final Set<String> FUNCTIONS = Set.of(
            "rgb", "rgba", "hsl", "hsla", "hwb", "lab", "lch", "oklab", "oklch", "color", "calc", "min", "max",
            "clamp");

    /// What a presentational attribute's value may be: a hex colour or a colour
    /// name, and nothing that could carry a second declaration.
    private static final Pattern ATTRIBUTE_COLOUR = Pattern.compile("#?[0-9A-Za-z]{1,32}");

    /// The declarations of `list` — a `style=` or a rule's body — that survive,
    /// in order.
    static List<Declaration> parse(String list) {
        var kept = new ArrayList<Declaration>();
        for (var written : StyleText.declarations(list)) {
            var declaration = declaration(written);
            if (declaration != null) {
                kept.add(declaration);
            }
        }
        return kept;
    }

    /// What `element`'s presentational attributes say, as declarations a rule
    /// would have written: `bgcolor` on a table, a row, a cell or the body, and
    /// `color` on a `font`.
    ///
    /// These are what mail written for clients that read no CSS is coloured with,
    /// and they rank below everything else, as a browser ranks them.
    static List<Declaration> presentational(Element element) {
        var kept = new ArrayList<Declaration>(1);
        var background = element.attribute("bgcolor");
        if (background != null) {
            addColour(kept, "background-color", background);
        }
        if ("font".equals(element.tag())) {
            var colour = element.attribute("color");
            if (colour != null) {
                addColour(kept, "color", colour);
            }
        }
        return kept;
    }

    private static void addColour(List<Declaration> kept, String property, String written) {
        var value = written.strip();
        if (!ATTRIBUTE_COLOUR.matcher(value).matches()) {
            return;
        }
        // A bare `bgcolor="ffcc00"` is a hex colour without its `#`, which every
        // browser accepts and CSS does not.
        if (!value.startsWith("#") && CssColor.parse(value) == null && CssColor.parse("#" + value) != null) {
            value = "#" + value;
        }
        if (CssColor.parse(value) == null) {
            return;
        }
        var declaration = declaration(property + ": " + value);
        if (declaration != null) {
            kept.add(declaration);
        }
    }

    /// One `property: value`, or null when it is refused.
    static @Nullable Declaration declaration(String written) {
        var colon = written.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        var property = written.substring(0, colon).strip().toLowerCase(Locale.ROOT);
        var value = written.substring(colon + 1).strip();
        if (!ALLOWED.contains(property) || !ComputedStyle.isProperty(property) || value.isEmpty()) {
            return null;
        }
        if (value.indexOf('\\') >= 0 || value.indexOf('{') >= 0 || value.indexOf('}') >= 0 || value.indexOf(';') >= 0) {
            return null;
        }
        var parsed = parsed(property, value);
        if (parsed == null || !safe(parsed.value())) {
            return null;
        }
        if (isKeyword(parsed.value(), "revert") || isKeyword(parsed.value(), "revert-layer")) {
            return null;
        }
        return switch (property) {
            // A colour, and nothing else: the shorthand is where an image would be.
            case "background" -> CssColor.parse(parsed.value()) == null ? null : parsed("background-color", value);
            case "display" -> isKeyword(parsed.value(), "none") ? parsed : null;
            case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left" ->
                negative(parsed.value()) ? null : parsed;
            default -> parsed;
        };
    }

    /// `property: value` as the toolkit's parser reads it, without `!important`,
    /// or null when it cannot read it.
    private static @Nullable Declaration parsed(String property, String value) {
        try {
            var rules = CssParser.parse("x{" + property + ":" + value + "}");
            if (rules.size() != 1 || rules.getFirst().declarations().size() != 1) {
                return null;
            }
            var declaration = rules.getFirst().declarations().getFirst();
            if (!declaration.property().equals(property)) {
                return null;
            }
            return declaration.important()
                    ? new Declaration(declaration.property(), declaration.value(), false, 1, 1)
                    : declaration;
        } catch (RuntimeException e) {
            // A mail's CSS is input, and a value the parser cannot read is a
            // value this drops; nothing about it is worth more than that.
            return null;
        }
    }

    /// Whether every function in `value` is one that cannot fetch or read
    /// anything.
    private static boolean safe(List<Token> value) {
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) && !FUNCTIONS.contains(token.text().toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (token.is(TokenType.AT_KEYWORD)) {
                return false;
            }
        }
        return true;
    }

    private static boolean negative(List<Token> value) {
        for (var token : value) {
            if ((token.is(TokenType.DIMENSION) || token.is(TokenType.NUMBER) || token.is(TokenType.PERCENTAGE))
                    && token.numeric() < 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeyword(List<Token> value, String keyword) {
        var words =
                value.stream().filter(token -> !token.is(TokenType.WHITESPACE)).toList();
        return words.size() == 1
                && words.getFirst().is(TokenType.IDENT)
                && words.getFirst().text().equalsIgnoreCase(keyword);
    }
}
