package dev.goldberry.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.font.FontFeatures;
import dev.goldberry.text.font.FontVariation;

/// The values of `font-stretch`, `font-variation-settings`,
/// `font-feature-settings` and `font-variant-numeric`, read beside
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

    /// `font-feature-settings`: `normal`, or a comma list of a four-letter
    /// string and, optionally, a non-negative integer, `on` or `off`. A tag
    /// alone is `on`. A tag written twice keeps its last value.
    static Optional<List<FontFeatures.Setting>> featureSettings(List<Token> value) {
        var tokens = meaningful(value);
        if (tokens.size() == 1 && tokens.getFirst().isIdent("normal")) {
            return Optional.of(List.of());
        }
        var settings = new LinkedHashMap<String, FontFeatures.Setting>();
        var current = new ArrayList<Token>();
        for (var i = 0; i <= tokens.size(); i++) {
            if (i < tokens.size() && !tokens.get(i).is(TokenType.COMMA)) {
                current.add(tokens.get(i));
                continue;
            }
            var setting = feature(current);
            if (setting.isEmpty()) {
                return Optional.empty();
            }
            settings.remove(setting.get().tag());
            settings.put(setting.get().tag(), setting.get());
            current.clear();
        }
        return Optional.of(List.copyOf(settings.values()));
    }

    /// One `"liga" 0`, `"tnum" on` or `"smcp"`.
    private static Optional<FontFeatures.Setting> feature(List<Token> pair) {
        if (pair.isEmpty() || pair.size() > 2 || !pair.getFirst().is(TokenType.STRING)) {
            return Optional.empty();
        }
        var tag = pair.getFirst().text();
        if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
            return Optional.empty();
        }
        if (pair.size() == 1) {
            return Optional.of(new FontFeatures.Setting(tag, 1));
        }
        var last = pair.getLast();
        if (last.isIdent("on")) {
            return Optional.of(new FontFeatures.Setting(tag, 1));
        }
        if (last.isIdent("off")) {
            return Optional.of(new FontFeatures.Setting(tag, 0));
        }
        // A CSS integer: digits, with no point and no exponent, and not negative.
        if (last.is(TokenType.NUMBER) && INTEGER.matcher(last.text()).matches() && last.numeric() <= Integer.MAX_VALUE) {
            return Optional.of(new FontFeatures.Setting(tag, (int) last.numeric()));
        }
        return Optional.empty();
    }

    /// A non-negative CSS integer as written.
    private static final Pattern INTEGER = Pattern.compile("\\+?[0-9]+");

    /// The feature each `font-variant-numeric` keyword turns on.
    private static final Map<String, String> NUMERIC = Map.of(
            "lining-nums", "lnum",
            "oldstyle-nums", "onum",
            "proportional-nums", "pnum",
            "tabular-nums", "tnum",
            "diagonal-fractions", "frac",
            "stacked-fractions", "afrc",
            "ordinal", "ordn",
            "slashed-zero", "zero");

    /// Which keywords exclude each other: one of each pair at most.
    private static final List<List<String>> NUMERIC_GROUPS = List.of(
            List.of("lining-nums", "oldstyle-nums"),
            List.of("proportional-nums", "tabular-nums"),
            List.of("diagonal-fractions", "stacked-fractions"),
            List.of("ordinal"),
            List.of("slashed-zero"));

    /// `font-variant-numeric`: `normal`, or at most one keyword of each kind
    /// (figures, spacing, fractions, `ordinal`, `slashed-zero`) in any order.
    static Optional<List<FontFeatures.Setting>> variantNumeric(List<Token> value) {
        var tokens = meaningful(value);
        if (tokens.size() == 1 && tokens.getFirst().isIdent("normal")) {
            return Optional.of(List.of());
        }
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        var used = new boolean[NUMERIC_GROUPS.size()];
        var settings = new ArrayList<FontFeatures.Setting>(tokens.size());
        for (var token : tokens) {
            if (!token.is(TokenType.IDENT)) {
                return Optional.empty();
            }
            var keyword = token.text().toLowerCase(Locale.ROOT);
            var feature = NUMERIC.get(keyword);
            if (feature == null) {
                return Optional.empty();
            }
            var group = groupOf(keyword);
            if (used[group]) {
                return Optional.empty();
            }
            used[group] = true;
            settings.add(new FontFeatures.Setting(feature, 1));
        }
        return Optional.of(List.copyOf(settings));
    }

    private static int groupOf(String keyword) {
        for (var i = 0; i < NUMERIC_GROUPS.size(); i++) {
            if (NUMERIC_GROUPS.get(i).contains(keyword)) {
                return i;
            }
        }
        throw new IllegalStateException(keyword + " is in no group");
    }

    private static List<Token> meaningful(List<Token> value) {
        return value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
    }
}
