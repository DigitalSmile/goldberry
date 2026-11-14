package dev.goldberry.i18n;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A message in ICU's syntax, parsed once and formatted for any locale.
///
/// ```java
/// var files = IcuMessage.parse("{count, plural, one {# файл} few {# файла} many {# файлов} other {# файла}}");
/// files.format(Locale.forLanguageTag("ru"), Map.of("count", 3));   // "3 файла"
/// ```
///
/// What is understood, which is what an application's strings use:
///
/// - `{name}`: the argument, a number formatted for the locale;
/// - `{name, number}`: the same, said outright;
/// - `{name, plural, …}` and `{name, selectordinal, …}`: a sub-message per
///   category (`one {…}`, `other {…}`) or exact value (`=0 {…}`), with an
///   optional `offset:n`, and `#` inside for the number less the offset;
/// - `{name, select, …}`: a sub-message per value, `other {…}` for the rest;
/// - apostrophes as ICU has them: `''` is one, and `'{'` quotes syntax.
///
/// Every plural and select must have `other`, as ICU requires, and a message
/// that breaks a rule is refused when parsed rather than when shown.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public final class IcuMessage {

    /// One piece of a parsed message.
    private sealed interface Part {}

    private record Text(String text) implements Part {}

    /// `#`, inside a plural.
    private record Pound() implements Part {}

    private record Argument(String name) implements Part {}

    private record Plural(String name, PluralType type, long offset, Map<String, List<Part>> cases) implements Part {}

    private record Select(String name, Map<String, List<Part>> cases) implements Part {}

    private final String pattern;
    private final List<Part> parts;

    private IcuMessage(String pattern, List<Part> parts) {
        this.pattern = pattern;
        this.parts = parts;
    }

    /// `pattern`, parsed.
    ///
    /// @throws IllegalArgumentException if it is not a message: an unbalanced
    ///         brace, an unknown argument type, a plural without `other`
    public static IcuMessage parse(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        var parser = new Parser(pattern);
        var parts = parser.message(false);
        if (parser.at < pattern.length()) {
            throw parser.error("an unmatched '}'");
        }
        return new IcuMessage(pattern, parts);
    }

    /// Parses and formats in one step, for a message shown once.
    public static String format(String pattern, Locale locale, Map<String, ?> arguments) {
        return parse(pattern).format(locale, arguments);
    }

    /// This message with `arguments`, in `locale`. An argument that is not
    /// given is shown as its own `{name}`, so a missing one is visible rather
    /// than silent.
    public String format(Locale locale, Map<String, ?> arguments) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(arguments, "arguments");
        var out = new StringBuilder();
        write(parts, locale, arguments, null, out);
        return out.toString();
    }

    /// The pattern this was parsed from.
    public String pattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return pattern;
    }

    private static void write(
            List<Part> parts, Locale locale, Map<String, ?> arguments, @Nullable Number pound, StringBuilder out) {
        for (var part : parts) {
            switch (part) {
                case Text(var text) -> out.append(text);
                case Pound() -> out.append(pound == null ? "#" : number(locale, pound));
                case Argument(var name) -> out.append(value(locale, arguments, name));
                case Select(var name, var cases) -> {
                    var chosen = arguments.containsKey(name) ? String.valueOf(arguments.get(name)) : "other";
                    var branch = cases.get(chosen);
                    write(branch != null ? branch : other(cases), locale, arguments, pound, out);
                }
                case Plural(var name, var type, var offset, var cases) -> {
                    if (!(arguments.get(name) instanceof Number value)) {
                        out.append('{').append(name).append('}');
                        continue;
                    }
                    var decimal = decimal(value);
                    var exact = cases.get("=" + decimal.stripTrailingZeros().toPlainString());
                    var shifted = decimal.subtract(BigDecimal.valueOf(offset));
                    if (exact != null) {
                        write(exact, locale, arguments, shifted, out);
                        continue;
                    }
                    var category = PluralRules.of(locale, type).select(PluralOperands.of(shifted));
                    var branch = cases.get(category.keyword());
                    write(branch != null ? branch : other(cases), locale, arguments, shifted, out);
                }
            }
        }
    }

    /// The `other` case, which parsing made sure of.
    private static List<Part> other(Map<String, List<Part>> cases) {
        return Objects.requireNonNull(cases.get("other"), "parsed with an other case");
    }

    private static String value(Locale locale, Map<String, ?> arguments, String name) {
        if (!arguments.containsKey(name)) {
            return "{" + name + "}";
        }
        var value = arguments.get(name);
        return value instanceof Number number ? number(locale, number) : String.valueOf(value);
    }

    private static String number(Locale locale, Number number) {
        return NumberFormat.getInstance(locale).format(number instanceof BigDecimal ? number : decimal(number));
    }

    /// `number` as the decimal a plural rule reads: whole numbers exactly, and
    /// a `double` without the trailing zeros its binary form has none of.
    private static BigDecimal decimal(Number number) {
        return switch (number) {
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Long _, Integer _, Short _, Byte _ -> BigDecimal.valueOf(number.longValue());
            default -> {
                var stripped = BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
                yield stripped.scale() < 0 ? stripped.setScale(0) : stripped;
            }
        };
    }

    /// A recursive-descent reader of the pattern.
    private static final class Parser {

        private final String text;
        private int at;

        Parser(String text) {
            this.text = text;
        }

        /// Parts up to the end, or up to the `}` that closes a sub-message.
        List<Part> message(boolean inPlural) {
            var parts = new ArrayList<Part>();
            var literal = new StringBuilder();
            while (at < text.length()) {
                var c = text.charAt(at);
                if (c == '}') {
                    break;
                }
                if (c == '\'') {
                    at++;
                    quoted(literal, inPlural);
                    continue;
                }
                if (c == '{') {
                    flush(literal, parts);
                    at++;
                    parts.add(argument());
                    continue;
                }
                if (c == '#' && inPlural) {
                    flush(literal, parts);
                    parts.add(new Pound());
                    at++;
                    continue;
                }
                literal.append(c);
                at++;
            }
            flush(literal, parts);
            return List.copyOf(parts);
        }

        /// After an apostrophe: `''` is one, a quote before syntax runs to the
        /// next lone apostrophe, and anything else is a literal apostrophe.
        private void quoted(StringBuilder literal, boolean inPlural) {
            if (at < text.length() && text.charAt(at) == '\'') {
                literal.append('\'');
                at++;
                return;
            }
            if (at >= text.length() || ("{}|".indexOf(text.charAt(at)) < 0 && !(inPlural && text.charAt(at) == '#'))) {
                literal.append('\'');
                return;
            }
            while (at < text.length()) {
                var c = text.charAt(at++);
                if (c == '\'') {
                    if (at < text.length() && text.charAt(at) == '\'') {
                        literal.append('\'');
                        at++;
                        continue;
                    }
                    return;
                }
                literal.append(c);
            }
        }

        private static void flush(StringBuilder literal, List<Part> parts) {
            if (!literal.isEmpty()) {
                parts.add(new Text(literal.toString()));
                literal.setLength(0);
            }
        }

        /// What follows a `{`, up to and including its `}`.
        private Part argument() {
            var name = word();
            if (name.isEmpty()) {
                throw error("an argument with no name");
            }
            skipSpace();
            if (accept('}')) {
                return new Argument(name);
            }
            expect(',');
            var kind = word();
            skipSpace();
            return switch (kind) {
                case "number" -> {
                    // A style after it, `{n, number, integer}`, is read and
                    // left to the locale's own format.
                    if (accept(',')) {
                        word();
                        skipSpace();
                    }
                    expect('}');
                    yield new Argument(name);
                }
                case "plural", "selectordinal" -> {
                    expect(',');
                    var type = kind.equals("plural") ? PluralType.CARDINAL : PluralType.ORDINAL;
                    skipSpace();
                    long offset = 0;
                    if (text.startsWith("offset:", at)) {
                        at += "offset:".length();
                        offset = Long.parseLong(word());
                    }
                    yield new Plural(name, type, offset, cases(true));
                }
                case "select" -> {
                    expect(',');
                    yield new Select(name, cases(false));
                }
                default ->
                    throw error("an argument of type \"" + kind + "\", which is not number, plural, "
                            + "selectordinal or select");
            };
        }

        /// `key {message}` pairs up to the argument's `}`, which must include
        /// `other`.
        private Map<String, List<Part>> cases(boolean plural) {
            var cases = new LinkedHashMap<String, List<Part>>();
            while (true) {
                skipSpace();
                if (accept('}')) {
                    break;
                }
                var key = word();
                if (key.isEmpty()) {
                    throw error("a case with no key");
                }
                if (plural && !key.startsWith("=") && PluralCategory.named(key).isEmpty()) {
                    throw error("\"" + key + "\", which is no plural category");
                }
                skipSpace();
                expect('{');
                var message = message(plural);
                expect('}');
                cases.put(key, message);
            }
            if (!cases.containsKey("other")) {
                throw error("a plural or select with no \"other\" case");
            }
            return Map.copyOf(cases);
        }

        private String word() {
            skipSpace();
            var start = at;
            while (at < text.length()) {
                var c = text.charAt(at);
                if (Character.isWhitespace(c) || c == ',' || c == '{' || c == '}') {
                    break;
                }
                at++;
            }
            return text.substring(start, at);
        }

        private void skipSpace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        private boolean accept(char c) {
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            skipSpace();
            if (!accept(c)) {
                throw error("'" + c + "' expected");
            }
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + " at " + at + " in: " + text);
        }
    }
}
