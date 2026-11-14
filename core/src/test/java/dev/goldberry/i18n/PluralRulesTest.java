package dev.goldberry.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// CLDR's plural rules, held to CLDR's own samples: every rule of every
/// locale lists numbers it covers, and each must select its category.
@DisplayName("plural rules")
class PluralRulesTest {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");

    @Test
    @DisplayName("put every one of CLDR's sample numbers in the category CLDR lists it under, in every locale")
    void cldrSamples() {
        var checked = 0;
        var wrong = new ArrayList<String>();
        for (var type : PluralType.values()) {
            for (var locale : PluralRules.locales(type)) {
                var rules = PluralRules.of(Locale.forLanguageTag(locale), type);
                assertEquals(locale, rules.locale(), "found by its own tag");
                for (var category : PluralCategory.values()) {
                    for (var sample : samples(rules.rule(category))) {
                        checked++;
                        var selected = rules.select(PluralOperands.parse(sample));
                        if (selected != category) {
                            wrong.add(type + " " + locale + " " + sample + ": " + selected + ", CLDR says " + category);
                        }
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.size() + " of " + checked + " samples wrong:\n" + String.join("\n", wrong));
        assertTrue(checked > 10_000, "the samples were read: " + checked);
    }

    @Test
    @DisplayName("count in Russian: 1 файл, 3 файла, 5 файлов, 21 файл, 11 файлов, 1.5 файла")
    void russian() {
        var rules = PluralRules.of(RUSSIAN, PluralType.CARDINAL);
        assertEquals(PluralCategory.ONE, rules.select(1));
        assertEquals(PluralCategory.FEW, rules.select(3));
        assertEquals(PluralCategory.MANY, rules.select(5));
        assertEquals(PluralCategory.ONE, rules.select(21));
        assertEquals(PluralCategory.MANY, rules.select(11));
        assertEquals(PluralCategory.OTHER, rules.select(new BigDecimal("1.5")));
        assertEquals(
                Set.of(PluralCategory.ONE, PluralCategory.FEW, PluralCategory.MANY, PluralCategory.OTHER),
                rules.categories());
    }

    @Test
    @DisplayName("order in English: 1st, 2nd, 3rd, 4th, 11th, 22nd")
    void englishOrdinals() {
        var rules = PluralRules.of(Locale.ENGLISH, PluralType.ORDINAL);
        assertEquals(PluralCategory.ONE, rules.select(1));
        assertEquals(PluralCategory.TWO, rules.select(2));
        assertEquals(PluralCategory.FEW, rules.select(3));
        assertEquals(PluralCategory.OTHER, rules.select(4));
        assertEquals(PluralCategory.OTHER, rules.select(11));
        assertEquals(PluralCategory.TWO, rules.select(22));
    }

    @Test
    @DisplayName("tell 1 from 1.0, as English does")
    void visibleZeros() {
        var rules = PluralRules.of(Locale.ENGLISH, PluralType.CARDINAL);
        assertEquals(PluralCategory.ONE, rules.select(1));
        assertEquals(PluralCategory.OTHER, rules.select(new BigDecimal("1.0")));
    }

    @Test
    @DisplayName("take a region's rules where CLDR has them, and the language's otherwise")
    void fallback() {
        assertEquals(
                "pt-PT",
                PluralRules.of(Locale.forLanguageTag("pt-PT"), PluralType.CARDINAL)
                        .locale());
        assertEquals(
                "pt",
                PluralRules.of(Locale.forLanguageTag("pt-BR"), PluralType.CARDINAL)
                        .locale());
        assertEquals(
                PluralCategory.ONE,
                PluralRules.of(Locale.forLanguageTag("pt-BR"), PluralType.CARDINAL)
                        .select(0));
        assertEquals(
                PluralCategory.OTHER,
                PluralRules.of(Locale.forLanguageTag("pt-PT"), PluralType.CARDINAL)
                        .select(0));
        var unknown = PluralRules.of(Locale.forLanguageTag("qaa"), PluralType.CARDINAL);
        assertEquals("und", unknown.locale());
        assertEquals(Set.of(PluralCategory.OTHER), unknown.categories());
    }

    /// The numbers a CLDR rule lists after `@integer` and `@decimal`, ranges
    /// expanded: `2~4` is 2, 3 and 4, and `0.0~1.5` steps by its last digit.
    static List<String> samples(String rule) {
        var samples = new ArrayList<String>();
        var at = rule.indexOf('@');
        if (at < 0) {
            return samples;
        }
        for (var group : rule.substring(at).split("@")) {
            var trimmed = group.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            var list = trimmed.substring(trimmed.indexOf(' ') + 1);
            for (var item : list.split(",")) {
                var sample = item.strip();
                if (sample.isEmpty() || sample.equals("…")) {
                    continue;
                }
                var range = sample.indexOf('~');
                if (range < 0) {
                    samples.add(sample);
                    continue;
                }
                var low = new BigDecimal(sample.substring(0, range));
                var high = new BigDecimal(sample.substring(range + 1));
                var step = BigDecimal.ONE.movePointLeft(low.scale());
                for (var value = low; value.compareTo(high) <= 0 && samples.size() < 100_000; value = value.add(step)) {
                    samples.add(value.toPlainString());
                }
            }
        }
        return samples;
    }
}
