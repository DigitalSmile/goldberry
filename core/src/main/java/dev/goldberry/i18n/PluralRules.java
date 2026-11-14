package dev.goldberry.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Which plural category a number is in, in one language: CLDR's rules for
/// every locale it has, cardinal and ordinal.
///
/// ```java
/// var russian = PluralRules.of(Locale.forLanguageTag("ru"), PluralType.CARDINAL);
/// russian.select(3);   // FEW:   3 файла
/// russian.select(5);   // MANY:  5 файлов
/// russian.select(21);  // ONE:  21 файл
/// ```
///
/// The rules are CLDR's own (version 49), read from the toolkit's jar once.
/// A locale CLDR does not know falls back to its language, and a language it
/// does not know has `OTHER` alone, which is right for a language without
/// plural forms and harmless for any other.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public final class PluralRules {

    private static final String RESOURCE = "plurals.txt";

    /// CLDR's locale for a language it does not know: `other` alone.
    private static final String UNDETERMINED = "und";

    /// One category's rule.
    private record Rule(PluralCategory category, PluralCondition condition, String text) {}

    /// Every locale's rules, by type.
    private record Tables(Map<String, List<Rule>> cardinal, Map<String, List<Rule>> ordinal) {

        Map<String, List<Rule>> of(PluralType type) {
            return switch (type) {
                case CARDINAL -> cardinal;
                case ORDINAL -> ordinal;
            };
        }
    }

    /// Read on first use.
    private static final class Table {
        private static final Tables RULES = load();
    }

    private final String locale;
    private final PluralType type;
    private final List<Rule> rules;

    private PluralRules(String locale, PluralType type, List<Rule> rules) {
        this.locale = locale;
        this.type = type;
        this.rules = rules;
    }

    /// The rules of `locale`'s language, or of the language alone where CLDR
    /// has nothing more particular.
    public static PluralRules of(Locale locale, PluralType type) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(type, "type");
        var byLocale = Table.RULES.of(type);
        for (var candidate : candidates(locale)) {
            var rules = byLocale.get(candidate);
            if (rules != null) {
                return new PluralRules(candidate, type, rules);
            }
        }
        var undetermined = byLocale.get(UNDETERMINED);
        return new PluralRules(
                UNDETERMINED,
                type,
                undetermined != null
                        ? undetermined
                        : List.of(new Rule(PluralCategory.OTHER, new PluralCondition.Always(), "")));
    }

    /// The CLDR locale ids to try for `locale`, most particular first: `pt-PT`,
    /// then `pt`.
    private static List<String> candidates(Locale locale) {
        var names = new LinkedHashSet<String>();
        var language = locale.getLanguage();
        if (!locale.getCountry().isEmpty()) {
            names.add(language + "-" + locale.getCountry());
        }
        if (!locale.getScript().isEmpty()) {
            names.add(language + "-" + locale.getScript());
        }
        if (!language.isEmpty()) {
            names.add(language);
        }
        return List.copyOf(names);
    }

    /// The category `number` is in.
    public PluralCategory select(PluralOperands number) {
        Objects.requireNonNull(number, "number");
        for (var rule : rules) {
            if (rule.condition().matches(number)) {
                return rule.category();
            }
        }
        return PluralCategory.OTHER;
    }

    /// The category of a whole number.
    public PluralCategory select(long number) {
        return select(PluralOperands.of(number));
    }

    /// The category of a decimal as written, its trailing zeros counting.
    public PluralCategory select(BigDecimal number) {
        return select(PluralOperands.of(number));
    }

    /// The categories this language uses, `OTHER` always among them.
    public Set<PluralCategory> categories() {
        var categories = new LinkedHashSet<PluralCategory>();
        for (var rule : rules) {
            categories.add(rule.category());
        }
        categories.add(PluralCategory.OTHER);
        return Set.copyOf(categories);
    }

    /// The CLDR rule of `category` as CLDR writes it, samples included, or
    /// empty for a category this language does not use: what a test checks
    /// against CLDR's own sample numbers.
    public String rule(PluralCategory category) {
        for (var rule : rules) {
            if (rule.category() == category) {
                return rule.text();
            }
        }
        return "";
    }

    /// The CLDR locale these rules are, `und` when CLDR knows nothing of the
    /// language.
    public String locale() {
        return locale;
    }

    /// Every CLDR locale id with rules of `type`.
    public static Set<String> locales(PluralType type) {
        return Table.RULES.of(Objects.requireNonNull(type, "type")).keySet();
    }

    @Override
    public String toString() {
        return "PluralRules[" + locale + ", " + type + "]";
    }

    private static Tables load() {
        var cardinal = new HashMap<String, List<Rule>>();
        var ordinal = new HashMap<String, List<Rule>>();
        var stream = PluralRules.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("the toolkit's jar has no " + RESOURCE);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                var fields = line.split("\t", 4);
                var byLocale = fields[0].equals("ordinal") ? ordinal : cardinal;
                var category = PluralCategory.named(fields[2])
                        .orElseThrow(() -> new IllegalStateException("not a plural category: " + fields[2]));
                byLocale.computeIfAbsent(fields[1], ignored -> new ArrayList<>())
                        .add(new Rule(category, PluralRuleParser.parse(fields[3]), fields[3]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("reading " + RESOURCE, e);
        }
        return new Tables(frozen(cardinal), frozen(ordinal));
    }

    private static Map<String, List<Rule>> frozen(Map<String, List<Rule>> byLocale) {
        var copy = new HashMap<String, List<Rule>>();
        byLocale.forEach((locale, list) -> copy.put(locale, List.copyOf(list)));
        return Map.copyOf(copy);
    }
}
