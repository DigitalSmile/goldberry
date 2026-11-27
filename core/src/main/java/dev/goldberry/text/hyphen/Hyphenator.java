package dev.goldberry.text.hyphen;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

/// Finds places where a displayed line can end with an extra hyphen. Offsets
/// address the original string and can be used without changing its contents.
public final class Hyphenator {

    private static final int[] NO_BREAKS = {};

    private Hyphenator() {}

    /// The bundled dictionary used for a locale, or null when none applies.
    /// Languages and regions sharing a table can share its break candidates.
    public static @Nullable Locale dictionaryLocale(Locale locale) {
        return locale.getLanguage().equals("en")
                        && (locale.getScript().isEmpty() || locale.getScript().equals("Latn"))
                ? Locale.US
                : null;
    }

    /// Returns possible break offsets in ascending UTF-16 order. English uses
    /// the American English patterns and their exceptions. Other languages,
    /// explicit non-Latin scripts, and words outside the pattern alphabet
    /// have no automatic breaks.
    public static int[] breaks(String text, Locale locale) {
        if (dictionaryLocale(locale) == null || text.length() < EnglishPatterns.LEFT_MIN + EnglishPatterns.RIGHT_MIN) {
            return NO_BREAKS;
        }
        var breaks = IntStream.builder();
        var words = BreakIterator.getWordInstance(Locale.ENGLISH);
        words.setText(text);
        for (int start = words.first(), end = words.next();
                end != BreakIterator.DONE;
                start = end, end = words.next()) {
            if (end - start >= EnglishPatterns.LEFT_MIN + EnglishPatterns.RIGHT_MIN && asciiWord(text, start, end)) {
                EnglishPatterns.INSTANCE.addBreaks(text.substring(start, end).toLowerCase(Locale.ROOT), start, breaks);
            }
        }
        return breaks.build().toArray();
    }

    private static boolean asciiWord(String text, int start, int end) {
        for (var i = start; i < end; i++) {
            var c = text.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }
}
