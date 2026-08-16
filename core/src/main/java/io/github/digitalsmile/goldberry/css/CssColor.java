package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Reads a CSS colour into the `0xAARRGGBB` int the paint layer takes.
///
/// That packing is not this class's choice — it is what
/// [io.github.digitalsmile.goldberry.Frame] already takes, **not premultiplied**
/// (ADR-0031). Converting here rather than at the paint call keeps one spelling
/// of a colour in the toolkit.
///
/// ## What is supported
///
/// `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`/`rgba()` in both the legacy
/// comma form and the modern space form, `transparent`, and the sixteen named
/// colours of CSS Level 1.
///
/// The full 148-name CSS colour list is deliberately not here. Goldberry's
/// palette comes from Nord through custom properties (§10); a stylesheet reaching
/// for `papayawhip` is not doing what the theming mechanism is for, and 148 names
/// is 148 chances for `grey`/`gray` confusion to look like a toolkit bug.
public final class CssColor {

    private CssColor() {
    }

    /// Fully transparent, and what an unparseable colour is never silently
    /// turned into.
    public static final int TRANSPARENT = 0x00000000;

    /// The CSS Level 1 names, plus the two spellings of grey.
    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0xFF000000),
            Map.entry("silver", 0xFFC0C0C0),
            Map.entry("gray", 0xFF808080),
            Map.entry("grey", 0xFF808080),
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("maroon", 0xFF800000),
            Map.entry("red", 0xFFFF0000),
            Map.entry("purple", 0xFF800080),
            Map.entry("fuchsia", 0xFFFF00FF),
            Map.entry("green", 0xFF008000),
            Map.entry("lime", 0xFF00FF00),
            Map.entry("olive", 0xFF808000),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("navy", 0xFF000080),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("teal", 0xFF008080),
            Map.entry("aqua", 0xFF00FFFF));

    /// Parses a colour from a declaration's value tokens.
    ///
    /// @return the colour as `0xAARRGGBB`, or null if these tokens are not one
    public static Integer parse(List<Token> value) {
        var tokens = withoutWhitespace(value);
        if (tokens.isEmpty()) {
            return null;
        }
        var first = tokens.getFirst();

        if (first.is(TokenType.HASH) && tokens.size() == 1) {
            return fromHex(first.text());
        }
        if (first.is(TokenType.IDENT) && tokens.size() == 1) {
            var name = first.text().toLowerCase(Locale.ROOT);
            if (name.equals("transparent")) {
                return TRANSPARENT;
            }
            return NAMED.get(name);
        }
        if (first.is(TokenType.FUNCTION)) {
            var name = first.text().toLowerCase(Locale.ROOT);
            if (name.equals("rgb") || name.equals("rgba")) {
                return fromRgb(tokens);
            }
        }
        return null;
    }

    /// `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`.
    private static Integer fromHex(String digits) {
        for (var i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) {
                return null;
            }
        }
        return switch (digits.length()) {
            // Each digit doubled: #abc is #aabbcc, which is why this is not a
            // shift of the 6-digit path.
            case 3 -> 0xFF000000 | expand(digits.charAt(0)) << 16
                    | expand(digits.charAt(1)) << 8 | expand(digits.charAt(2));
            case 4 -> expand(digits.charAt(3)) << 24 | expand(digits.charAt(0)) << 16
                    | expand(digits.charAt(1)) << 8 | expand(digits.charAt(2));
            case 6 -> 0xFF000000 | (int) Long.parseLong(digits, 16);
            // CSS writes the alpha last; the packed form wants it first.
            case 8 -> {
                var rgba = Long.parseLong(digits, 16);
                yield (int) (((rgba & 0xFF) << 24) | (rgba >>> 8));
            }
            default -> null;
        };
    }

    private static int expand(char digit) {
        var v = Character.digit(digit, 16);
        return v * 16 + v;
    }

    /// `rgb(46 52 64)`, `rgb(46, 52, 64)`, `rgba(46, 52, 64, 0.5)`,
    /// `rgb(46 52 64 / 50%)`.
    private static Integer fromRgb(List<Token> tokens) {
        if (!tokens.getLast().is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        var arguments = tokens.subList(1, tokens.size() - 1);

        var channels = new int[3];
        var channel = 0;
        Double alpha = null;
        var afterSlash = false;

        for (var token : arguments) {
            if (token.is(TokenType.COMMA)) {
                continue;
            }
            if (token.isDelim('/')) {
                afterSlash = true;
                continue;
            }
            if (!token.is(TokenType.NUMBER) && !token.is(TokenType.PERCENTAGE)) {
                return null;
            }
            // A percentage channel is of 255; a percentage alpha is of 1.
            if (afterSlash || channel == 3) {
                if (alpha != null) {
                    return null;
                }
                alpha = token.is(TokenType.PERCENTAGE) ? token.numeric() / 100 : token.numeric();
                continue;
            }
            var raw = token.is(TokenType.PERCENTAGE) ? token.numeric() / 100 * 255 : token.numeric();
            channels[channel++] = clamp255(raw);
        }

        if (channel != 3) {
            return null;
        }
        var a = alpha == null ? 255 : clamp255(alpha * 255);
        return a << 24 | channels[0] << 16 | channels[1] << 8 | channels[2];
    }

    /// CSS clamps out-of-range channels rather than rejecting them, which is the
    /// forgiving choice in the one place forgiveness costs nothing: the author
    /// clearly meant "as much red as there is".
    private static int clamp255(double value) {
        return (int) Math.round(Math.max(0, Math.min(255, value)));
    }

    private static List<Token> withoutWhitespace(List<Token> value) {
        return value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
    }
}
