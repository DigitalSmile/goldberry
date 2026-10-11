package dev.goldberry.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.font.FontVariation;

/// The values of `font-stretch` and `font-variation-settings`, read beside
/// [ComputedStyle] rather than inside it.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#typography).
final class FontValues {

    /// CSS's nine width keywords, as the percentages they are.
    private static final Map<String, Double> STRETCHES = Map.of(
            "ultra-condensed", 50.0,
            "extra-condensed", 62.5,
            "condensed", 75.0,
            "semi-condensed", 87.5,
            "normal", 100.0,
            "semi-expanded", 112.5,
            "expanded", 125.0,
            "extra-expanded", 150.0,
            "ultra-expanded", 200.0);

    private FontValues() {}

    /// `font-stretch`: a keyword, or a percentage from 50% to 200%.
    static Optional<Double> stretch(List<Token> value) {
        var tokens = meaningful(value);
        if (tokens.size() != 1) {
            return Optional.empty();
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.IDENT)) {
            return Optional.ofNullable(STRETCHES.get(token.text().toLowerCase(Locale.ROOT)));
        }
        if (token.is(TokenType.PERCENTAGE)) {
            var percent = token.numeric();
            return percent >= FontVariation.MIN_STRETCH && percent <= FontVariation.MAX_STRETCH
                    ? Optional.of(percent)
                    : Optional.empty();
        }
        return Optional.empty();
    }

    /// `font-variation-settings`: `normal`, or a comma list of a four-letter
    /// string and a number. A tag written twice keeps its last value.
    static Optional<List<FontVariation.Setting>> variationSettings(List<Token> value) {
        var tokens = meaningful(value);
        if (tokens.size() == 1 && tokens.getFirst().isIdent("normal")) {
            return Optional.of(List.of());
        }
        var settings = new LinkedHashMap<String, FontVariation.Setting>();
        var current = new ArrayList<Token>();
        for (var i = 0; i <= tokens.size(); i++) {
            if (i < tokens.size() && !tokens.get(i).is(TokenType.COMMA)) {
                current.add(tokens.get(i));
                continue;
            }
            var setting = setting(current);
            if (setting.isEmpty()) {
                return Optional.empty();
            }
            settings.remove(setting.get().tag());
            settings.put(setting.get().tag(), setting.get());
            current.clear();
        }
        return Optional.of(List.copyOf(settings.values()));
    }

    /// One `"wght" 500`.
    private static Optional<FontVariation.Setting> setting(List<Token> pair) {
        if (pair.size() != 2 || !pair.getFirst().is(TokenType.STRING)) {
            return Optional.empty();
        }
        var tag = pair.getFirst().text();
        if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
            return Optional.empty();
        }
        var number = CssLength.parseNumber(List.of(pair.getLast()));
        if (number == null || !Double.isFinite(number)) {
            return Optional.empty();
        }
        return Optional.of(new FontVariation.Setting(tag, number));
    }

    private static List<Token> meaningful(List<Token> value) {
        return value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
    }
}
