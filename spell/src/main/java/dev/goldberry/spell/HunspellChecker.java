package dev.goldberry.spell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.analysis.hunspell.SortingStrategy;
import org.apache.lucene.analysis.hunspell.TimeoutPolicy;

import dev.goldberry.text.spell.SpellChecker;

/// A Hunspell dictionary, read by Lucene's Hunspell.
///
/// ```java
/// var english = HunspellChecker.load(Locale.US, Path.of("en_US.aff"), Path.of("en_US.dic"));
/// SpellCheckers.install(english);
/// ```
///
/// The `.aff` file's `SET` line says how both files are encoded, and the
/// reader follows it, so an ISO 8859 dictionary loads as a UTF-8 one does. The
/// words are kept in memory, sorted there, and nothing is written to disk.
///
/// A suggestion stops after [#SUGGEST_MILLIS] and answers what it found by
/// then, which matters for the languages whose affix rules make the search
/// long. One lock serialises the checks and the suggestions, because Lucene's
/// checker keeps scratch buffers. A check takes microseconds, and the fields ask
/// from one background thread at a time.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class HunspellChecker implements SpellChecker {

    /// How long a suggestion may search, in milliseconds.
    public static final long SUGGEST_MILLIS = 250;

    private final Locale locale;
    private final Hunspell hunspell;
    private final ReentrantLock lock = new ReentrantLock();

    private HunspellChecker(Locale locale, Hunspell hunspell) {
        this.locale = locale;
        this.hunspell = hunspell;
    }

    /// The dictionary in `affix` and `words`, for `locale`.
    ///
    /// @throws IOException if either file cannot be read or is not a dictionary
    public static HunspellChecker load(Locale locale, Path affix, Path words) throws IOException {
        try (var aff = Files.newInputStream(affix);
                var dic = Files.newInputStream(words)) {
            return load(locale, aff, dic);
        }
    }

    /// The same, from two streams the caller closes: a dictionary in the
    /// application's own resources.
    public static HunspellChecker load(Locale locale, InputStream affix, InputStream words) throws IOException {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(affix, "affix");
        Objects.requireNonNull(words, "words");
        try {
            var dictionary = new Dictionary(affix, List.of(words), false, SortingStrategy.inMemory());
            return new HunspellChecker(locale, new Hunspell(dictionary, TimeoutPolicy.RETURN_PARTIAL_RESULT, () -> {}));
        } catch (ParseException e) {
            throw new IOException("not a Hunspell dictionary for " + locale.toLanguageTag() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Locale locale() {
        return locale;
    }

    @Override
    public boolean isCorrect(String word) {
        Objects.requireNonNull(word, "word");
        lock.lock();
        try {
            return hunspell.spell(word);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> suggest(String word, int limit) {
        Objects.requireNonNull(word, "word");
        if (limit <= 0) {
            return List.of();
        }
        lock.lock();
        try {
            if (hunspell.spell(word)) {
                return List.of();
            }
            var found = hunspell.suggest(word, SUGGEST_MILLIS);
            return List.copyOf(found.subList(0, Math.min(limit, found.size())));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "HunspellChecker[" + locale.toLanguageTag() + "]";
    }
}
