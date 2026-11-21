package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.contrast.Contrast;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;

/// The colour arithmetic of [Colours#ADAPT]: which declarations carry a colour,
/// what a colour is against the ground it sits on, and what to write when it is
/// unreadable there.
final class Inks {

    private Inks() {}

    /// Dark ink: near-black, a little warm, as mail clients set body text.
    static final int DARK = 0xFF1F2328;

    /// Light ink.
    static final int LIGHT = 0xFFFFFFFF;

    /// A link on a light ground and on a dark one: the two blues a reader expects
    /// a link in, each 4.5:1 or better against the grounds it is chosen for.
    private static final int LINK_ON_LIGHT = 0xFF0B57D0;

    private static final int LINK_ON_DARK = 0xFF8AB4F8;

    /// Text colours, held to [Contrast#TEXT_FLOOR] against their ground.
    static final Set<String> INK = Set.of("color", "text-decoration-color");

    /// Colours that are not text: a fill and a box's edges.
    static final Set<String> SOLID = Set.of(
            "background-color",
            "border-color",
            "border-top-color",
            "border-right-color",
            "border-bottom-color",
            "border-left-color");

    /// Shorthands that may carry a colour among their other values.
    static final Set<String> MIXED =
            Set.of("border", "border-top", "border-right", "border-bottom", "border-left", "text-decoration");

    /// The functions a colour is written with.
    private static final Set<String> COLOUR_FUNCTIONS =
            Set.of("rgb", "rgba", "hsl", "hsla", "hwb", "lab", "lch", "oklab", "oklch", "color", "color-mix");

    /// The ground `declaration` paints when it is an opaque `background-color`,
    /// or null. A translucent one is no ground: what shows through it is not
    /// known here.
    static @Nullable Integer ground(@Nullable Declaration declaration) {
        if (declaration == null) {
            return null;
        }
        var colour = CssColor.parse(declaration.value());
        return colour != null && Contrast.isOpaque(colour) ? colour : null;
    }

    /// The colour `declaration` writes, or null for a keyword such as `inherit`.
    static @Nullable Integer colour(Declaration declaration) {
        return CssColor.parse(declaration.value());
    }

    /// Whether `ink` reads against `ground` as body text must.
    static boolean reads(int ink, int ground) {
        return Contrast.isOpaque(ink) && Contrast.meets(ink, ground, Contrast.TEXT_FLOOR);
    }

    /// Dark or light, whichever reads better on `ground`.
    static int legible(int ground) {
        return Contrast.ratio(DARK, ground) >= Contrast.ratio(LIGHT, ground) ? DARK : LIGHT;
    }

    /// A link's blue for `ground`, or [#legible] where neither blue reads.
    static int link(int ground) {
        if (reads(LINK_ON_LIGHT, ground)) {
            return LINK_ON_LIGHT;
        }
        if (reads(LINK_ON_DARK, ground)) {
            return LINK_ON_DARK;
        }
        return legible(ground);
    }

    /// `#rrggbb` for an opaque colour.
    static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }

    /// `declaration` with every colour among its values taken out, or null when
    /// nothing else was in it: `border: 1px solid #ccc` becomes `border: 1px
    /// solid`, which draws in the text's colour.
    static @Nullable Declaration withoutColour(Declaration declaration) {
        var kept = new ArrayList<Token>();
        var value = declaration.value();
        var i = 0;
        while (i < value.size()) {
            var token = value.get(i);
            if (token.is(TokenType.FUNCTION)
                    && COLOUR_FUNCTIONS.contains(token.text().toLowerCase(Locale.ROOT))) {
                i = pastClose(value, i);
                continue;
            }
            if ((token.is(TokenType.HASH) || token.is(TokenType.IDENT)) && CssColor.parse(List.of(token)) != null) {
                i++;
                continue;
            }
            kept.add(token);
            i++;
        }
        var trimmed = trim(kept);
        if (trimmed.isEmpty()) {
            return null;
        }
        return new Declaration(declaration.property(), trimmed, false, declaration.line(), declaration.column());
    }

    private static int pastClose(List<Token> value, int open) {
        var depth = 0;
        for (var i = open; i < value.size(); i++) {
            var token = value.get(i);
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN) && --depth == 0) {
                return i + 1;
            }
        }
        return value.size();
    }

    private static List<Token> trim(List<Token> tokens) {
        var start = 0;
        var end = tokens.size();
        while (start < end && tokens.get(start).is(TokenType.WHITESPACE)) {
            start++;
        }
        while (end > start && tokens.get(end - 1).is(TokenType.WHITESPACE)) {
            end--;
        }
        // Two spaces where a colour was between them read the same as one.
        var out = new ArrayList<Token>(end - start);
        for (var i = start; i < end; i++) {
            var token = tokens.get(i);
            if (token.is(TokenType.WHITESPACE)
                    && !out.isEmpty()
                    && out.getLast().is(TokenType.WHITESPACE)) {
                continue;
            }
            out.add(token);
        }
        return out;
    }
}
