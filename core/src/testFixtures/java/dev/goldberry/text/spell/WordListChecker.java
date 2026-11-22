package dev.goldberry.text.spell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/// A checker over a fixed list of words, for tests that need spelling without
/// an engine or a dictionary file.
///
/// A word is correct when it is in the list, or its small-letter form is.
/// Suggestions are the listed words nearest by edit distance, nearest first.
/// It counts how many words it was asked about, so a test can see a cache
/// working.
public final class WordListChecker implements SpellChecker {

    private final Locale locale;
    private final Set<String> words;
    private final AtomicInteger asked = new AtomicInteger();

    public WordListChecker(Locale locale, Set<String> words) {
        this.locale = Objects.requireNonNull(locale, "locale");
        this.words = Set.copyOf(words);
    }

    /// English, knowing `words`.
    public static WordListChecker english(String... words) {
        return new WordListChecker(Locale.US, Set.of(words));
    }

    @Override
    public Locale locale() {
        return locale;
    }

    @Override
    public boolean isCorrect(String word) {
        asked.incrementAndGet();
        return words.contains(word) || words.contains(word.toLowerCase(locale));
    }

    @Override
    public List<String> suggest(String word, int limit) {
        var near = new ArrayList<String>();
        for (var candidate : words) {
            if (distance(word.toLowerCase(locale), candidate) <= 2) {
                near.add(candidate);
            }
        }
        near.sort(Comparator.<String>comparingInt(candidate -> distance(word.toLowerCase(locale), candidate))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(near.subList(0, Math.min(limit, near.size())));
    }

    /// How many words this checker has been asked about.
    public int asked() {
        return asked.get();
    }

    private static int distance(String a, String b) {
        var previous = new int[b.length() + 1];
        var current = new int[b.length() + 1];
        for (var j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (var i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (var j = 1; j <= b.length(); j++) {
                var cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            var swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
